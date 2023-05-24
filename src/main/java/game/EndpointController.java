package game;

import game.Game;
import game.Deal;

import java.util.HashMap;
import java.util.ArrayList;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
// e.g. INTERNAL_SERVER_ERROR

/**
 *  NOTES
 *
 *  - currently using asserts for exceptions (more terse), the downside being that reason
 *    string is not returned to caller (later can throw ResponseStatusException instead)
 */

// ========== Constants, etc. ========== //

// status for POST/PATCH notification, or status of an object
class Status {
    public static final String OK       = "ok";
    public static final String NEW      = "new";
    public static final String UPDATE   = "update";
    public static final String ACTIVE   = "active";
    public static final String COMPLETE = "complete";
}

// ========== Endpoint Classes ========== //

class EPSession {
    String token;
    String status;

    ArrayList<EPGame> gameList = new ArrayList<EPGame>();

    public EPSession(String token, String status) {
        this.token  = token;
        this.status = status;
    }
}

class EPGame {
    String token;
    int    nGame;
    String status;

    Game   game;

    ArrayList<EPDeal> dealList = new ArrayList<EPDeal>();

    public EPGame(GameStatus game, String status) {
        this.token  = game.token();
        this.nGame  = game.nGame();
        this.status = status;
    }
}

/**
 *  Representation of cards (consistent with Deal POST request):
 *  - Deck Cards - int[24]
 *    - 0-4  : Position 0 Hand
 *    - 5-9  : Position 1 Hand
 *    - 10-14: Position 2 Hand
 *    - 15-19: Position 3 Hand (dealer)
 *    - 20   : Turn Card
 *    - 21-23: Buries
 *
 *  - Card Values - int (0-23)
 *    - 0-5  : 9C, 10C, JC, QC, KC, AC
 *    - 6-11 : 9D, 10D, JD, QD, KD, AD
 *    - 12-17: 9H, 10H, JH, QH, KH, AH
 *    - 18-23: 9S, 10S, JS, QS, KS, AS
 *
 *  Representation of player positions:
 *  - 0: first to bid/lead first trick
 *  - 1: second to bid/play first trick
 *  - 2: third to bid/play first trick
 *  - 3: fourth to bid/play first trick (dealer)
 *
 *  Representation of suits (for bidding):
 *  - (-1): pass
 *  -   0 : clubs
 *  -   1 : diamonds
 *  -   2 : hearts
 *  -   3 : spades
 */
class EPDeal {
    String token;
    int    nGame;
    int    nDeal;
    String status;
    int[]  cards;
    int    pos;

    Deal   deal;

    public EPDeal(DealInfo deal, String status) {
        this.token  = deal.token();
        this.nGame  = deal.nGame();
        this.nDeal  = deal.nDeal();
        this.status = status;
        this.cards  = deal.cards();
        this.pos    = deal.pos();
    }
}

/**
 *  Representation of suits:
 *  - 0: clubs
 *  - 1: diamonds
 *  - 2: hearts
 *  - 3: spades
 */
class EPBid {
}

class EPPlay {
}

// ========== Data Structures ========== //

// generic response
record GenStatus(String status, String info) {

    public GenStatus(String status) {
        this(status, null);
    }
}

// POST request, PATCH request
record SessionStatus(String token, String status, String info) {

    public SessionStatus(String token, String status) {
        this(token, status, null);
    }

    public SessionStatus(EPSession sess) {
        this(sess.token, sess.status);
    }
}

// POST response
class SessionProtocol {

    // 9C, 10C, JC, ..., 9D, 10D, JD, ..., QS, KS, AS
    static final int[] cards = {3, 7, 11, 15, 19, 23,
                                2, 6, 10, 14, 18, 22,
                                1, 5,  9, 13, 17, 21,
                                0, 4,  8, 12, 16, 20};
    // Clubs, Diamonds, Hearts, Spades
    static final int[] suits = {3, 2, 1, 0};

    public int[] getCards() {
        return cards;
    }

    public int[] getSuits() {
        return suits;
    }
}

// POST request, PATCH request
record GameStatus(String token, int nGame, String status, String info) {

    public GameStatus(String token, int nGame, String status) {
        this(token, nGame, status, null);
    }

    public GameStatus(EPGame game) {
        this(game.token, game.nGame, game.status);
    }
}

// POST request
record DealInfo(String token, int nGame, int nDeal, String status, int[] cards, int pos) {
}

// PATCH request
record DealStatus(String token, int nGame, int nDeal, String status, String info) {

    public DealStatus(String token, int nGame, int nDeal, String status) {
        this(token, nGame, nDeal, status, null);
    }

    public DealStatus(EPDeal deal) {
        this(deal.token, deal.nGame, deal.nDeal, deal.status);
    }
}

// ========== Controller Class ========== //

@RestController
public class EndpointController {

    HashMap<String, EPSession> sessionMap = new HashMap<String, EPSession>();

    // ---------- Session ---------- //

    @PostMapping("/session")
    public SessionProtocol postSession(@RequestBody SessionStatus req) {
        // check request parameters
        assert req.status().equals(Status.NEW) : "bad req status: " + req.status();
        assert !sessionMap.containsKey(req.token()) : "token exists: " + req.token();

        // create/add new session
        EPSession sess = new EPSession(req.token(), Status.ACTIVE);
        sessionMap.put(req.token(), sess);
        return new SessionProtocol();
    }

    @PatchMapping("/session")
    public SessionStatus patchSession(@RequestBody SessionStatus req) {
        // check request parameters
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        assert req.status().equals(Status.COMPLETE) : "bad req status: " + req.status();

        // remove and update status, if complete
        EPSession sess = sessionMap.remove(req.token());
        sess.status = req.status();
        return new SessionStatus(sess);
    }

    // ---------- Game ---------- //

    @PostMapping("/game")
    public GameStatus postGame(@RequestBody GameStatus req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EPSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + req.status();

        // check request parameters
        assert req.status().equals(Status.NEW) : "bad req status: " + req.status();
        assert req.nGame() == sess.gameList.size() : "bad nGame value: " + req.nGame();

        // create/add new game
        EPGame game = new EPGame(req, Status.ACTIVE);
        sess.gameList.add(game);
        return new GameStatus(game);
    }

    @PatchMapping("/game")
    public GameStatus patchGame(@RequestBody GameStatus req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EPSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + req.status();

        // check request parameters
        switch (req.status()) {
        case Status.UPDATE:
        case Status.COMPLETE:
            break;
        default:
            assert false : "bad req status: " + req.status();
        }
        assert req.nGame() == sess.gameList.size() - 1 : "bad nGame value: " + req.nGame();
        EPGame game = sess.gameList.get(req.nGame());
        // update stats/info here (leave status alone)!!!

        // clean up and update status, if complete
        if (req.status().equals(Status.COMPLETE)) {
            game.status = req.status();
            // delete reference to underlying Game!!!
            // leave on gameList (will be cleaned up with `sess`)
        }
        return new GameStatus(game);
    }

    // ---------- Deal ---------- //

    @PostMapping("/deal")
    public DealStatus postDeal(@RequestBody DealInfo req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EPSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + req.status();

        // get game, check status
        assert req.nGame() == sess.gameList.size() - 1 : "bad nGame value: " + req.nGame();
        EPGame game = sess.gameList.get(req.nGame());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + req.status();

        // check request parameters
        assert req.status().equals(Status.NEW) : "bad req status: " + req.status();
        assert req.nDeal() == game.dealList.size() : "bad nDeal value: " + req.nDeal();

        // create/add new deal
        EPDeal deal = new EPDeal(req, Status.ACTIVE);
        game.dealList.add(deal);
        return new DealStatus(deal);
    }

    @PatchMapping("/deal")
    public DealStatus patchDeal(@RequestBody DealStatus req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EPSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + req.status();

        // get game, check status
        assert req.nGame() == sess.gameList.size() - 1 : "bad nGame value: " + req.nGame();
        EPGame game = sess.gameList.get(req.nGame());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + req.status();

        // check request parameters
        switch (req.status()) {
        case Status.UPDATE:
        case Status.COMPLETE:
            break;
        default:
            assert false : "bad req status: " + req.status();
        }
        assert req.nDeal() == game.dealList.size() - 1 : "bad nDeal value: " + req.nDeal();
        EPDeal deal = game.dealList.get(req.nDeal());
        // update stats/info here (leave status alone)!!!

        // clean up and update status, if complete
        if (req.status().equals(Status.COMPLETE)) {
            deal.status = req.status();
            // delete reference to underlying Deal!!!
            // leave on dealList (will be cleaned up with `game`)
        }
        return new DealStatus(deal);
    }

    // ---------- Bid ---------- //

    @GetMapping("/bid")
    public GenStatus getBid() {
        return new GenStatus(Status.OK);
    }

    @PostMapping("/bid")
    public GenStatus postBid() {
        return new GenStatus(Status.OK);
    }

    // ---------- Swap ---------- //

    @GetMapping("/swap")
    public GenStatus getSwap() {
        return new GenStatus(Status.OK);
    }

    @PostMapping("/swap")
    public GenStatus postSwap() {
        return new GenStatus(Status.OK);
    }

    // ---------- Defense ---------- //

    @GetMapping("/defense")
    public GenStatus getDefense() {
        return new GenStatus(Status.OK);
    }

    @PostMapping("/defense")
    public GenStatus postDefense() {
        return new GenStatus(Status.OK);
    }

    // ---------- Play ---------- //

    @GetMapping("/play")
    public GenStatus getPlay() {
        return new GenStatus(Status.OK);
    }

    @PostMapping("/play")
    public GenStatus postPlay() {
        return new GenStatus(Status.OK);
    }
}
