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
cards for all of the hands, but is only called upon the bid or play for the local player
position(s) it represents.  For each move within the game (i.e. bid, card swap, defense
declaration, or card play), the client/coordinator either notifies the server of a remote
player's action (implemented by some other strategy), or requests an action from a local
player.  It is assumed that there is no cross-talk between hands (i.e. cheating) on the
server side.

See [API_NOTES](blob/master/API_NOTES.md) for a more detailed description of the API.

The formal schema for the API is defined in [openapi.yaml](blob/master/openapi.yaml) (OAS
3.0).  This specification may be viewed using the free [Swagger
UI](https://swagger.io/tools/swagger-ui/) tool.

As an overview, here is the general call sequence:

1. **New session notification** (POST /session)
2. **New game notification** (POST /game)
3. **New deal notification** (POST /deal)
   - Cards dealt to each of the hands, and turn card, are indicated
4. For each bidding position, either:
   a. **Bid request** (GET /bid), if bidder is local to server
      - Bid information (suit + going alone?, or pass) is indicated
   b. **Bid notification** (POST /bid), if bidder is remote; or:
      - Position and bid information (suit + going alone?, or pass) are indicated
5. Bidding continues until declaration or two rounds of passes
   - If all positions pass twice, then **Deal complete notification** (step 11) is called
6. If first round declaration (turn card picked up), either:
   a. **Swap request** (GET /swap), if dealer is local to server
      - Card from hand (to be exchanged with turn card), or "no swap", is indicated
   b. **Swap notification** (POST /swap), if dealer is remote; or:
      - Note that this notification is optional (not needed by the server)
7. After a bid declaration<sup>†</sup>, either:
   a. **Defense request** (GET /defense), if defender position is local to server
      - Whether to defend alone is indicated
   b. **Defense notification** (POST /defense), if defender position is remote
      - Position and whether to defend alone are indicated
8. **New trick notification** (POST /trick)
   - Lead position (first to play) is indicated
9. For each card playing position within a trick, either:
   a. **Play request** (GET /defense), if player position is local to server
      - Card (from hand) to play is indicated
   b. **Play notification** (POST /defense), if player position is remote
      - Position and card played are indicated
10. **Trick complete notifcation** (PATCH /trick) is called at the end of each trick
    - Winner of trick is indicated
11. **Deal complete notification** (PATCH /deal) is called at the end of each deal
    - Winner of deal and number of points awarded are indicated
11. **Game complete notification** (PATCH /game) is called at the end of each game
    - Note that winner of game is not indicated (since there is no notion of table
      positions in the current interface design)
11. **Session complete notification** (PATCH /session) is called at the end of each
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


## Related Projects

[EuchreEndpoint2](https://github.com/crashka/EuchreEndpoint2) – a tighter, next-generation
version of the current API (and server implementation?) that creates separate sessions for
individual players (i.e. eliminates the "shadow server" model), recognizes persistent
player identities, and provides explicit game, match, tournament, and Elo rating
information for more advanced, context-based strategies

## License

This project is licensed under the terms of the MIT License.
