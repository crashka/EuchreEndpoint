package game;

import game.Game;
import game.Deal;
import game.GameState;
import game.DealState;

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
class Status
{
    public static final String OK       = "ok";
    public static final String NEW      = "new";
    public static final String UPDATE   = "update";
    public static final String ACTIVE   = "active";
    public static final String COMPLETE = "complete";
}

// hardwired protocol mapping for cards and suits
class Protocol
{
    // 9C, 9D, 9H, 9S, 10C, ..., KS, AC, AD, AH, AS
    static final int[] cards = { 3,  2,  1,  0,
                                 7,  6,  5,  4,
                                11, 10,  9,  8,
                                15, 14, 13, 12,
                                19, 18, 17, 16,
                                23, 22, 21, 20};
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

class EpSession
{
    String   token;
    String   status;

    Protocol protocol;

    ArrayList<EpGame> gameList = new ArrayList<EpGame>();

    public EpSession(SessionInfo info, String status) {
        System.out.println(String.format("EpSession(%s, %s)", info.token(), status));
        this.token    = info.token();
        this.status   = status;  // ignore `info.status()`
        this.protocol = new Protocol();
    }
}

class EpGame
{
    static final int GAME_PTS = 10;

    String    token;
    int       gameNum;
    String    status;

    Game      game;
    int[]     points;
    GameState gameState;

    ArrayList<EpDeal> dealList = new ArrayList<EpDeal>();

    public EpGame(GameInfo info, String status) {
        System.out.println(String.format("EpGame(%d, %s)", info.gameNum(), status));
        this.token     = info.token();
        this.gameNum   = info.gameNum();
        this.status    = status;  // ignore `info.status()`

        this.game      = new Game();
        this.points    = new int[4];
        this.gameState = new GameState(this.points, GAME_PTS);
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
class EpDeal
{
    static final int DEALER_POS = 3;

    EpGame    parent;
    String    token;
    int       gameNum;
    int       dealNum;
    String    status;
    int[]     cards;

    int[]     cardMap;
    Deal      deal;
    // one-based indexing for subscript match (trick number), fake value for index 0
    // (first lead)
    int[]     win   = {(DEALER_POS + 1) % 4, -1, -1, -1, -1, -1};
    int[]     lead  = {-1, -1, -1, -1, -1, -1};  // suit led;
    int[]     trick = new int[4];  // tricks won (initialized to zeros);
    DealState dealState;
    // bidding stuff
    int       curBid;    // position
    int       lone;      // -1 or dclr
    int       declarer;  // -1 (pass) or dclr
    int       fintp;     // 4 (pass) or suit
    int       call;      // 0 - pass, 1 - call, 2 - alone
    int       cswap;
    // playing stuff
    int       curTrick;  // 0-4

    ArrayList<EpTrick> trickList = new ArrayList<EpTrick>();

    public EpDeal(EpGame parent, DealInfo info, String status) {
        System.out.println(String.format("EpDeal(%d, %s)", info.dealNum(), status));
        this.parent   = parent;
        this.token    = info.token();
        this.gameNum  = info.gameNum();
        this.dealNum  = info.dealNum();
        this.status   = status;  // ignore `info.status()`
        this.cards    = info.cards();
        this.cardMap  = new int[24];
        for (int i = 0; i < 24; i++) {
            this.cardMap[this.cards[i]] = i;
        }

        this.deal     = new Deal(this.cards, DEALER_POS);
        this.dealState = new DealState(win, lead, trick);
        // bidding stuff
        this.curBid   = -1;
        this.lone     = -1;
        this.declarer = -1;
        this.fintp    = -1;
        this.call     = -1;
        this.cswap    = -1;
        // playing stuff
        this.curTrick = -1;

        // start bidding phase of the deal
        this.deal.prepareBid();
    }

    public int[] getBid() {
        System.out.println("getBid()");
        int[] bidx = deal.bidder(++curBid, parent.gameState);
        return processBid(bidx);
    }

    public int[] notifyBid(int suit, boolean alone) {
        System.out.println(String.format("notifyBid(%d, %b)", suit, alone));
        int call   = suit < 0 ? 0 : (alone ? 2 : 1);
        int docall = call > 0 ? (call + suit * 10) : 0;
        int[] bidx = deal.bidder(++curBid, parent.gameState, docall);
        return processBid(bidx);
    }

    public int[] processBid(int[] bidx) {
        if (bidx[3] > 0) {
            lone     = bidx[0];  // -1 or dclr
            declarer = bidx[1];  // -1 (pass) or dclr
            fintp    = bidx[2];  // 4 (pass) or suit
            call     = bidx[3];  // 0 - pass, 1 - call, 2 - alone
            assert call == 1 || lone > -1;
        }
        return bidx;
    }

    public int bidRound() {
        return curBid / 4;
    }

    public int getSwap() {
        System.out.println("getSwap()");
        cswap = deal.swapCard(declarer, lone, 0);
        return cswap;
    }

    public int notifySwap(int card) {
        int cardpos = cardMap[card];
        assert cardpos >= 15 && cardpos <= 19;
        System.out.println(String.format("notifySwap(%d)", card));
        cswap = deal.swapCard(declarer, lone, 0, cardpos);
        return cswap;
    }

    public void startPlay() {
        System.out.println("startPlay()");
        // start play phase of the deal
        deal.preparePlay(declarer, fintp, lone, bidRound());
        // establish name of left bower
        Game.cardname[fintp][6] = "Jack of " + Game.suitx[3-fintp];
        deal.validateHands();
    }

    public void complete() {
    }
}

class EpTrick
{
    EpDeal parent;
    String token;
    int    gameNum;
    int    dealNum;
    int    trickNum;
    String status;

    int    curLead;   // position
    int    curSeq;    // 0-3 (within trick)
    int    curPlay;   // position

    int    leadsuit;
    int    winpos;
    int    winval;

    public EpTrick(EpDeal parent, TrickInfo info, String status) {
        System.out.println(String.format("EpTrick(%d, %s)", info.trickNum(), status));
        this.parent   = parent;
        this.token    = info.token();
        this.gameNum  = info.gameNum();
        this.dealNum  = info.dealNum();
        this.trickNum = info.trickNum();
        this.status   = status;  // ignore `info.status()`

        this.curLead  = -1;
        this.curSeq   = -1;  // play sequence (0-3)
        this.curPlay  = -1;

        this.leadsuit = -1;
        this.winpos   = -1;
        this.winval   = -1;

        // establish shortcuts for this trick
        int tr    = trickNum;
        int curaa = parent.win[tr];  // lead (previous winner)
        int curbb = (curaa+1)%4;     // second to play
        int curcc = (curaa+2)%4;     // third to play
        int curdd = (curaa+3)%4;     // fouth to play
        parent.deal.pos[tr+1] = new int[] {curaa, curbb, curcc, curdd};
    }

    public int getPlay(int pos, int trickSeq) {
        System.out.println("getPlay()");
        return processPlay(pos, trickSeq, -1);
    }

    public int notifyPlay(int pos, int trickSeq, int card) {
        System.out.println(String.format("notifyPlay(%d)", card));
        int suit = card % 4;
        int rank = card / 4;
        if (rank == 2) {
            if (suit == parent.fintp) {
                rank = 7;
            } else if (suit == 3 - parent.fintp) {
                rank = 6;
                suit = parent.fintp;
            }
        }
        return processPlay(pos, trickSeq, suit + rank * 10);
    }

    public int processPlay(int pos, int trickSeq, int playCard) {
        int tr      = trickNum;
        int pl      = ++curSeq;
        if (pl != trickSeq) {
            assert pl < trickSeq;
            System.out.println(String.format("Adjusting pl from %d to %d (pos %d)",
                                             pl, trickSeq, pos));
            pl = curSeq = trickSeq;
        }
        int playnum = tr*4+pl;
        int curpos  = parent.deal.pos[tr+1][pl];
        int partpos = (curpos+2)%4;

        if (parent.lone == partpos) { // partner NOT going alone
            assert false;  // we actually shouldn't be called
            return -1;
        }

        playCard = parent.deal.player(playnum, parent.dealState, playCard);
        parent.deal.validateHands();
        int cursuit = playCard%10;
        int currank = playCard/10;
        int curval  = -1;

        // compute card value
        if (cursuit == parent.fintp) {
            curval = 10+currank;
        } else if (leadsuit == -1) {
            curval = currank;
        } else if (cursuit == leadsuit) {
            curval = currank;
        }

        // evaluate if winning
        if (curval > winval) {
            parent.win[tr+1] = winpos = curpos;
            winval = curval;
        }

        if (leadsuit == -1) {
            parent.lead[tr+1] = leadsuit = cursuit;
        }

        if (currank == 7) {
            currank = 2;
        } else if (currank == 6) {
            currank = 2;
            cursuit = 3 - cursuit;
        }
        return cursuit + currank * 4;
    }

    public void complete() {
        parent.deal.updatePlay(trickNum);
    }
}

// ========== Data Structures ========== //

// Generic - POST/PATCH response
record EpStatus(String status, String info)
{
    public EpStatus(String status) {
        this(status, null);
    }
}

// Session - POST request
record SessionInfo(String token, String status)
{
}

// Session - POST response
record SessionProto(String token, String status, int[] cards, int[] suits)
{
    public SessionProto(EpSession sess) {
        this(sess.token, sess.status, sess.protocol.getCards(), sess.protocol.getSuits());
    }
}

// Session - PATCH request/response
record SessionStatus(String token, String status, String info)
{
    public SessionStatus(String token, String status) {
        this(token, status, null);
    }

    public SessionStatus(EpSession sess) {
        this(sess.token, sess.status);
    }
}

// Game - POST request
record GameInfo(String token, int gameNum, String status)
{
}

// Game - POST response, PATCH request/response
record GameStatus(String token, int gameNum, String status, String info)
{
    public GameStatus(String token, int gameNum, String status) {
        this(token, gameNum, status, null);
    }

    public GameStatus(EpGame game) {
        this(game.token, game.gameNum, game.status);
    }
}

// Deal - POST request
record DealInfo(String token, int gameNum, int dealNum, String status, int[] cards)
{
}

// Deal - POST response, PATCH request/response
record DealStatus(String token, int gameNum, int dealNum, String status, String info)
{
    public DealStatus(String token, int gameNum, int dealNum, String status) {
        this(token, gameNum, dealNum, status, null);
    }

    public DealStatus(EpDeal deal) {
        this(deal.token, deal.gameNum, deal.dealNum, deal.status);
    }
}

// Bid - GET response, POST request/response
record BidInfo(String token, int gameNum, int dealNum, int round, int turnCard, int pos,
               int suit, boolean alone)
{
    public BidInfo(BidInfo bid, int suggSuit, boolean suggAlone) {
        this(bid.token, bid.gameNum, bid.dealNum, bid.round, bid.turnCard, bid.pos,
             suggSuit, suggAlone);
    }
}

// Swap - GET response, POST request/response
record SwapInfo(String token, int gameNum, int dealNum, int declarerPos, int turnCard,
                int pos, int card)
{
    public SwapInfo(SwapInfo swap, int suggCard) {
        this(swap.token, swap.gameNum, swap.dealNum, swap.declarerPos, swap.turnCard,
             swap.pos, suggCard);
    }
}

// Defense - GET response, POST request/response
record DefenseInfo(String token, int gameNum, int dealNum, int declarerPos, int trumpSuit,
                   int pos, boolean alone)
{
    public DefenseInfo(DefenseInfo def, boolean suggAlone) {
        this(def.token, def.gameNum, def.dealNum, def.declarerPos, def.trumpSuit, def.pos,
             suggAlone);
    }
}

// Trick - POST request
record TrickInfo(String token, int gameNum, int dealNum, int trickNum, String status)
{
}

// Trick - POST response, PATCH request/response
record TrickStatus(String token, int gameNum, int dealNum, int trickNum, String status, String info)
{
    public TrickStatus(String token, int gameNum, int dealNum, int trickNum, String status) {
        this(token, gameNum, dealNum, trickNum, status, null);
    }

    public TrickStatus(EpTrick trick) {
        this(trick.token, trick.gameNum, trick.dealNum, trick.trickNum, trick.status);
    }
}

// Play - GET response, POST request/response
record PlayInfo(String token, int gameNum, int dealNum, int trickNum, int trickSeq,
                int pos, int card)
{
    public PlayInfo(PlayInfo play, int suggCard) {
        this(play.token, play.gameNum, play.dealNum, play.trickNum, play.trickSeq,
             play.pos, suggCard);
    }
}

// ========== Controller Class ========== //

@RestController
public class EndpointController
{
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
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

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
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

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
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // check request parameters
        assert req.status().equals(Status.NEW) : "bad req status: " + req.status();
        assert req.dealNum() == game.dealList.size() : "bad dealNum value: " + req.dealNum();

        // create/add new deal
        EpDeal deal = new EpDeal(game, req, Status.ACTIVE);
        game.dealList.add(deal);
        return new DealStatus(deal);
    }

    @PatchMapping("/deal")
    public DealStatus patchDeal(@RequestBody DealStatus req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

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
            deal.complete();
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
                          @RequestParam int turnCard,
                          @RequestParam int pos) {
        // get session, check status
        assert sessionMap.containsKey(token) : "unknown token: " + token;
        EpSession sess = sessionMap.get(token);
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert gameNum == sess.gameList.size() - 1 : "bad gameNum value: " + gameNum;
        EpGame game = sess.gameList.get(gameNum);
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // get deal, check status
        assert dealNum == game.dealList.size() - 1 : "bad dealNum value: " + dealNum;
        EpDeal deal = game.dealList.get(dealNum);
        assert deal.status.equals(Status.ACTIVE) : "bad deal status: " + deal.status;

        int[]   bidx  = deal.getBid();
        int     suit  = bidx[2];
        boolean alone = bidx[3] == 2;
        return new BidInfo(token, gameNum, dealNum, round, turnCard, pos, suit, alone);
    }

    @PostMapping("/bid")
    public BidInfo postBid(@RequestBody BidInfo req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // get deal, check status
        assert req.dealNum() == game.dealList.size() - 1 : "bad dealNum value: " + req.dealNum();
        EpDeal deal = game.dealList.get(req.dealNum());
        assert deal.status.equals(Status.ACTIVE) : "bad deal status: " + deal.status;

        int[]   bidx      = deal.notifyBid(req.suit(), req.alone());
        int     suggSuit  = bidx[2];
        boolean suggAlone = bidx[3] == 2;
        return new BidInfo(req, suggSuit, suggAlone);
    }

    // ---------- Swap ---------- //

    @GetMapping("/swap")
    public SwapInfo getSwap(@RequestParam String token,
                            @RequestParam int gameNum,
                            @RequestParam int dealNum,
                            @RequestParam int declarerPos,
                            @RequestParam int turnCard,
                            @RequestParam int pos,
                            @RequestParam List<Integer> swappableCards) {
        // get session, check status
        assert sessionMap.containsKey(token) : "unknown token: " + token;
        EpSession sess = sessionMap.get(token);
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert gameNum == sess.gameList.size() - 1 : "bad gameNum value: " + gameNum;
        EpGame game = sess.gameList.get(gameNum);
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // get deal, check status
        assert dealNum == game.dealList.size() - 1 : "bad dealNum value: " + dealNum;
        EpDeal deal = game.dealList.get(dealNum);
        assert deal.status.equals(Status.ACTIVE) : "bad deal status: " + deal.status;

        int swapCard = deal.getSwap();
        return new SwapInfo(token, gameNum, dealNum, declarerPos, turnCard, pos, swapCard);
    }

    @PostMapping("/swap")
    public SwapInfo postSwap(@RequestBody SwapInfo req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // get deal, check status
        assert req.dealNum() == game.dealList.size() - 1 : "bad dealNum value: " + req.dealNum();
        EpDeal deal = game.dealList.get(req.dealNum());
        assert deal.status.equals(Status.ACTIVE) : "bad deal status: " + deal.status;

        int suggCard = deal.notifySwap(req.card());
        return new SwapInfo(req, suggCard);
    }

    // ---------- Defense ---------- //

    @GetMapping("/defense")
    public DefenseInfo getDefense(@RequestParam String token,
                                  @RequestParam int gameNum,
                                  @RequestParam int dealNum,
                                  @RequestParam int declarerPos,
                                  @RequestParam int trumpSuit,
                                  @RequestParam int pos) {
        boolean alone = false;
        return new DefenseInfo(token, gameNum, dealNum, declarerPos, trumpSuit, pos, alone);
    }

    @PostMapping("/defense")
    public DefenseInfo postDefense(@RequestBody DefenseInfo req) {
        boolean suggAlone = false;
        return new DefenseInfo(req, suggAlone);
    }

    // ---------- Trick ---------- //

    @PostMapping("/trick")
    public TrickStatus postTrick(@RequestBody TrickInfo req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // get deal, check status
        assert req.dealNum() == game.dealList.size() - 1 : "bad dealNum value: " + req.dealNum();
        EpDeal deal = game.dealList.get(req.dealNum());
        assert deal.status.equals(Status.ACTIVE) : "bad deal status: " + deal.status;

        // check request parameters
        assert req.status().equals(Status.NEW) : "bad req status: " + req.status();
        assert req.trickNum() == deal.trickList.size() : "bad trickNum value: " + req.trickNum();

        // create/add new trick
        EpTrick trick = new EpTrick(deal, req, Status.ACTIVE);
        deal.trickList.add(trick);
        if (req.trickNum() == 0) {
            deal.startPlay();
        }
        return new TrickStatus(trick);
    }

    @PatchMapping("/trick")
    public TrickStatus patchTrick(@RequestBody TrickStatus req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // get deal, check status
        assert req.dealNum() == game.dealList.size() - 1 : "bad dealNum value: " + req.dealNum();
        EpDeal deal = game.dealList.get(req.dealNum());
        assert deal.status.equals(Status.ACTIVE) : "bad deal status: " + deal.status;

        // check request parameters
        switch (req.status()) {
        case Status.UPDATE:
        case Status.COMPLETE:
            break;
        default:
            assert false : "bad req status: " + req.status();
        }
        assert req.trickNum() == deal.trickList.size() - 1 : "bad trickNum value: " + req.trickNum();
        EpTrick trick = deal.trickList.get(req.trickNum());
        // update stats/info here (leave status alone)!!!

        // clean up and update status, if complete
        if (req.status().equals(Status.COMPLETE)) {
            trick.complete();
            trick.status = req.status();
            // delete reference to underlying Trick!!!
            // leave on trickList (will be cleaned up with `deal`)
        }
        return new TrickStatus(trick);
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
        // get session, check status
        assert sessionMap.containsKey(token) : "unknown token: " + token;
        EpSession sess = sessionMap.get(token);
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert gameNum == sess.gameList.size() - 1 : "bad gameNum value: " + gameNum;
        EpGame game = sess.gameList.get(gameNum);
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // get deal, check status
        assert dealNum == game.dealList.size() - 1 : "bad dealNum value: " + dealNum;
        EpDeal deal = game.dealList.get(dealNum);
        assert deal.status.equals(Status.ACTIVE) : "bad deal status: " + deal.status;

        // get trick, check status
        assert trickNum == deal.trickList.size() - 1 : "bad trickNum value: " + trickNum;
        EpTrick trick = deal.trickList.get(trickNum);
        assert trick.status.equals(Status.ACTIVE) : "bad trick status: " + trick.status;

        int playCard = trick.getPlay(pos, trickSeq);
        return new PlayInfo(token, gameNum, dealNum, trickNum, trickSeq, pos, playCard);
    }

    @PostMapping("/play")
    public PlayInfo postPlay(@RequestBody PlayInfo req) {
        // get session, check status
        assert sessionMap.containsKey(req.token()) : "unknown token: " + req.token();
        EpSession sess = sessionMap.get(req.token());
        assert sess.status.equals(Status.ACTIVE) : "bad session status: " + sess.status;

        // get game, check status
        assert req.gameNum() == sess.gameList.size() - 1 : "bad gameNum value: " + req.gameNum();
        EpGame game = sess.gameList.get(req.gameNum());
        assert game.status.equals(Status.ACTIVE) : "bad game status: " + game.status;

        // get deal, check status
        assert req.dealNum() == game.dealList.size() - 1 : "bad dealNum value: " + req.dealNum();
        EpDeal deal = game.dealList.get(req.dealNum());
        assert deal.status.equals(Status.ACTIVE) : "bad deal status: " + deal.status;

        // get trick, check status
        assert req.trickNum() == deal.trickList.size() - 1 : "bad trickNum value: " + req.trickNum();
        EpTrick trick = deal.trickList.get(req.trickNum());
        assert trick.status.equals(Status.ACTIVE) : "bad trick status: " + trick.status;

        int suggCard = trick.notifyPlay(req.pos(), req.trickSeq(), req.card());
        return new PlayInfo(req, suggCard);
    }
}
