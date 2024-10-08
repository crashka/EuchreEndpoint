# Euchre Endpoint

## Project Overview

This project is composed of two parts:

- The API specification for invoking Java-based algorithms from the `euchre-plt`
  tournament framework (see [euchre-plt](https://github.com/crashka/euchre-plt))
- A Spring Boot-based implementation of the API, which binds to the `EuchreBeta` engine
  (see [EuchreBeta](https://github.com/crashka/EuchreBeta))

The server side of this interface only plays hands for individual games.  The client side
(which we will call the "coordinator" in this documentation) manages the larger context of
deals and accounting for matches, tournaments, etc.

### API Specification

This version of the API represents a "shadow server", meaning that the server sees the
cards for all of the hands, but is only called upon to bid or play for the local player
position(s) it represents.  For each move within the game (i.e. bid, card swap, defense
declaration, or card play), the client/coordinator either notifies the server of a remote
player's action (implemented by some other strategy), or requests an action from a local
player.  It is assumed that there is no cross-talk between hands (i.e. cheating) on the
server side.

See [API_NOTES](API_NOTES.md) for a more detailed description of the API.

The formal schema for the API is defined in [openapi.yaml](openapi.yaml) (OAS 3.0).  This
specification may be viewed using the free [Swagger
UI](https://swagger.io/tools/swagger-ui/) tool.

As an overview of the API, here is the general call sequence:

1. **New session notification** (POST /session)
2. **New game notification** (POST /game)
3. **New deal notification** (POST /deal)
   - Cards dealt to each of the hands, and turn card, are indicated
4. For each bidding position, either:
   - **Bid request** (GET /bid), if bidder is local to server; or:
      - Bid information (suit + going alone?, or pass) is indicated
   - **Bid notification** (POST /bid), if bidder is remote
      - Position and bid information (suit + going alone?, or pass) are indicated
5. Bidding continues until declaration or two rounds of passes
   - If all positions pass twice, then **Deal complete notification** (PATCH /deal) is
     called (also see step 11)
6. If first round declaration (turn card picked up), either:
   - **Swap request** (GET /swap), if dealer is local to server; or:
      - Card from hand (to be exchanged with turn card), or "no swap", is indicated
   - **Swap notification** (POST /swap), if dealer is remote
      - Note that this notification is optional (not needed by the server)
7. After a bid declaration<sup>†</sup>, either:
   - **Defense request** (GET /defense), if defender position is local to server; or:
      - Whether to defend alone is indicated
   - **Defense notification** (POST /defense), if defender position is remote
      - Position and whether to defend alone are indicated
8. **New trick notification** (POST /trick)
   - Lead position (first to play) is indicated
9. For each card playing position within a trick, either:
   - **Play request** (GET /defense), if player position is local to server; or:
      - Card (from hand) to play is indicated
   - **Play notification** (POST /defense), if player position is remote
      - Position and card played are indicated
10. **Trick complete notifcation** (PATCH /trick) is called at the end of each trick
    - Winner of trick is indicated
11. **Deal complete notification** (PATCH /deal) is called at the end of each deal
    - Winner of deal and number of points awarded are indicated
12. **Game complete notification** (PATCH /game) is called at the end of each game
    - Note that winner of game is not indicated (since there is no notion of table
      positions in the current interface design)
13. **Session complete notification** (PATCH /session) is called at the end of each
    session
    - Note that session boundaries are purely defined by the client/coordinator

<sup>†</sup> Depending on the rules of euchre implemented and/or configured on the
client/coordinator side, "defend alone" declarations may only be allowed against "going
alone" bid declarations and/or only for defenders who have not yet passed in the round.

### Endpoint Implementation

Note that the JAR file for the EuchreBeta engine (or equivalent) must be installed in the
local Maven repository for the local build to work.  See [Guide to installing 3rd party
JARs](https://maven.apache.org/guides/mini/guide-3rd-party-jars-local.html).

## Project Status

**Current Version**

- **v1.0** - Completed integration with
  [EuchreBeta](https://github.com/crashka/EuchreBeta) (and
  [euchre-plt](https://github.com/crashka/euchre-plt)).

**To Do**

- Complete implementation of `/defense` endpoint (if/when defending alone is added to
  EuchreBeta).

## Related Projects

### [EuchreEndpoint2](https://github.com/crashka/EuchreEndpoint2)

A tighter, next-generation version of the current API (and server implementation?) that
creates separate sessions for individual players (i.e. eliminates the "shadow server"
model), recognizes persistent player identities, and provides explicit game, match,
tournament, and Elo rating information for more advanced, context-based strategies

## License

This project is licensed under the terms of the MIT License.
