package game;

import game.Game;
import game.Deal;

import java.util.List;
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

// hardwired protocol mapping for cards and suits
class Protocol {

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

// ========== Endpoint Classes ========== //

class EpSession {

    String   token;
    String   status;

    Protocol protocol = new Protocol();
    ArrayList<EpGame> gameList = new ArrayList<EpGame>();

    public EpSession(SessionInfo info, String status) {
        this.token  = info.token();
        this.status = status;  // ignore `info.status()`
    }
}

class EpGame {

    String token;
    int    gameNum;
    String status;

    Game   game = new Game();
    ArrayList<EpDeal> dealList = new ArrayList<EpDeal>();

    public EpGame(GameInfo info, String status) {
        this.token   = info.token();
        this.gameNum = info.gameNum();
        this.status  = status;  // ignore `info.status()`
    }

    public EpGame(GameStatus stat, String status) {
        this.token   = stat.token();
        this.gameNum = stat.gameNum();
        this.status  = status;  // ignore `stat.status()`
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
class EpDeal {

    static final int DEALER_POS = 3;

    String token;
    int    gameNum;
    int    dealNum;
    String status;
    int[]  cards;
    int    pos;

    Deal   deal;

    public EpDeal(DealInfo info, String status) {
        this.token   = info.token();
        this.gameNum = info.gameNum();
        this.dealNum = info.dealNum();
        this.status  = status;  // ignore `info.status()`
        this.cards   = info.cards();
        this.pos     = info.pos();

        this.deal    = new Deal(this.cards, DEALER_POS);
    }
}

// ========== Data Structures ========== //

// Generic - POST/PATCH response
record EpStatus(String status, String info) {

    public EpStatus(String status) {
        this(status, null);
    }
}

// Session - POST request
record SessionInfo(String token, String status) {
}

// Session - POST response
record SessionProto(String token, String status, int[] cardsProto, int[] suitsProto) {

    public SessionProto(EpSession sess) {
        this(sess.token, sess.status, sess.protocol.getCards(), sess.protocol.getSuits());
    }
}

// Session - PATCH request/response
record SessionStatus(String token, String status, String info) {

    public SessionStatus(String token, String status) {
        this(token, status, null);
    }

    public SessionStatus(EpSession sess) {
        this(sess.token, sess.status);
    }
}

// Game - POST request
record GameInfo(String token, int gameNum, String status) {
}

// Game - POST response, PATCH request/response
record GameStatus(String token, int gameNum, String status, String info) {

    public GameStatus(String token, int gameNum, String status) {
        this(token, gameNum, status, null);
    }

    public GameStatus(EpGame game) {
        this(game.token, game.gameNum, game.status);
    }
}

// Deal - POST request
record DealInfo(String token, int gameNum, int dealNum, String status, int[] cards, int pos) {
}

// Deal - POST response, PATCH request/response
record DealStatus(String token, int gameNum, int dealNum, String status, String info) {

    public DealStatus(String token, int gameNum, int dealNum, String status) {
        this(token, gameNum, dealNum, status, null);
    }

    public DealStatus(EpDeal deal) {
        this(deal.token, deal.gameNum, deal.dealNum, deal.status);
    }
}

// Bid - GET response, POST request/response
record BidInfo(String token, int gameNum, int dealNum, int round, int pos, int suit, boolean alone) {

    public BidInfo(BidInfo bid, int suggSuit, boolean suggAlone) {
        this(bid.token, bid.gameNum, bid.dealNum, bid.round, bid.pos, suggSuit, suggAlone);
    }
}

// Swap - GET response, POST request/response
record SwapInfo(String token, int gameNum, int dealNum, int pos, int card) {

    public SwapInfo(SwapInfo swap, int suggCard) {
        this(swap.token, swap.gameNum, swap.dealNum, swap.pos, suggCard);
    }
}

// Defense - GET response, POST request/response
record DefenseInfo(String token, int gameNum, int dealNum, int pos, boolean alone) {

    public DefenseInfo(DefenseInfo def, boolean suggAlone) {
        this(def.token, def.gameNum, def.dealNum, def.pos, suggAlone);
    }
}

// Play - GET response, POST request/response
record PlayInfo(String token, int gameNum, int dealNum, int trickNum, int trickSeq, int pos, int card) {

    public PlayInfo(PlayInfo play, int suggCard) {
        this(play.token, play.gameNum, play.dealNum, play.trickNum, play.trickSeq, play.pos, suggCard);
    }
}

// ========== Controller Class ========== //

@RestController
public class EndpointController {

    HashMap<String, EpSession> sessionMap = new HashMap<String, EpSession>();

    // ---------- Session ---------- //

    @PostMapping("/session")
    public SessionProto postSession(@RequestBody SessionInfo req) {
        // check request parameters
        assert req.status().equals(Status.NEW) : "bad req status: " + req.status();
        assert !sessionMap.containsKey(req.token()) : "token exists: " + req.token();

        // create/add new session
        EpSession sess = new EpSession(req, Status.ACTIVE);
        sessionMap.put(req.token(), sess);
        return new SessionProto(sess);
    }

    @PatchMapping("/session")
    public SessionStatus patchSession(@RequestBody SessionStatus req) {
        // check request parameters
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        assert req.status().equals(Status.COMPLETE) : "bad req status: " + req.status();

        // remove and update status, if complete
        EpSession sess = sessionMap.remove(req.token());
        sess.status = req.status();
        return new SessionStatus(sess);
    }

    // ---------- Game ---------- //

    @PostMapping("/game")
    public GameStatus postGame(@RequestBody GameInfo req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + req.status();

        // check request parameters
        assert req.status().equals(Status.NEW) : "bad req status: " + req.status();
        assert req.gameNum() == sess.gameList.size() : "bad gameNum value: " + req.gameNum();

        // create/add new game
        EpGame game = new EpGame(req, Status.ACTIVE);
        sess.gameList.add(game);
        return new GameStatus(game);
    }

    @PatchMapping("/game")
    public GameStatus patchGame(@RequestBody GameStatus req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + req.status();

        // check request parameters
        switch (req.status()) {
        case Status.UPDATE:
        case Status.COMPLETE:
            break;
        default:
            assert false : "bad req status: " + req.status();
        }
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
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
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + req.status();

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + req.status();

        // check request parameters
        assert req.status().equals(Status.NEW) : "bad req status: " + req.status();
        assert req.dealNum() == game.dealList.size() : "bad dealNum value: " + req.dealNum();

        // create/add new deal
        EpDeal deal = new EpDeal(req, Status.ACTIVE);
        game.dealList.add(deal);
        return new DealStatus(deal);
    }

    @PatchMapping("/deal")
    public DealStatus patchDeal(@RequestBody DealStatus req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + req.status();

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + req.status();

        // check request parameters
        switch (req.status()) {
        case Status.UPDATE:
        case Status.COMPLETE:
            break;
        default:
            assert false : "bad req status: " + req.status();
        }
        assert req.dealNum() == game.dealList.size() - 1 : "bad dealNum value: " + req.dealNum();
        EpDeal deal = game.dealList.get(req.dealNum());
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
    public BidInfo getBid(@RequestParam String token,
                          @RequestParam int gameNum,
                          @RequestParam int dealNum,
                          @RequestParam int round,
                          @RequestParam int pos) {
        int suit = -1;
        boolean alone = false;
        return new BidInfo(token, gameNum, dealNum, round, pos, suit, alone);
    }

    @PostMapping("/bid")
    public BidInfo postBid(@RequestBody BidInfo req) {
        int suggSuit = -1;
        boolean suggAlone = false;
        return new BidInfo(req, suggSuit, suggAlone);
    }

    // ---------- Swap ---------- //

    @GetMapping("/swap")
    public SwapInfo getSwap(@RequestParam String token,
                            @RequestParam int gameNum,
                            @RequestParam int dealNum,
                            @RequestParam int pos,
                            @RequestParam List<Integer> swappableCards) {
        int swapCard = -1;
        return new SwapInfo(token, gameNum, dealNum, pos, swapCard);
    }

    @PostMapping("/swap")
    public SwapInfo postSwap(@RequestBody SwapInfo req) {
        int suggCard = -1;
        return new SwapInfo(req, suggCard);
    }

    // ---------- Defense ---------- //

    @GetMapping("/defense")
    public DefenseInfo getDefense(@RequestParam String token,
                                  @RequestParam int gameNum,
                                  @RequestParam int dealNum,
                                  @RequestParam int pos) {
        boolean alone = false;
        return new DefenseInfo(token, gameNum, dealNum, pos, alone);
    }

    @PostMapping("/defense")
    public DefenseInfo postDefense(@RequestBody DefenseInfo req) {
        boolean suggAlone = false;
        return new DefenseInfo(req, suggAlone);
    }

    // ---------- Play ---------- //

    @GetMapping("/play")
    public PlayInfo getPlay(@RequestParam String token,
                            @RequestParam int gameNum,
                            @RequestParam int dealNum,
                            @RequestParam int trickNum,
                            @RequestParam int trickSeq,
                            @RequestParam int pos,
                            @RequestParam List<Integer> playableCards) {
        int playCard = -1;
        return new PlayInfo(token, gameNum, dealNum, trickNum, trickSeq, pos, playCard);
    }

    @PostMapping("/play")
    public PlayInfo postPlay(@RequestBody PlayInfo req) {
        int suggCard = -1;
        return new PlayInfo(req, suggCard);
    }
}
