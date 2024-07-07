# Euchre Endpoint #

## API Specification ##

### Notes ###

This version of the API represents a "shadow server", wherein the server is capable of
playing all hands, but the client controls which position actually makes the bids (and
defenses) and plays the cards for a game.  It is assumed that there is no cross-talk
between hands (i.e. cheating) on the server side.

Note that sequential data (e.g. counters and lists) are all zero-based.  For UI/output
interfaces, we may want to display "game number", "deal number", and "trick number" as
N+1.  For other internal indexable things (e.g. card, position, suit, etc.), we will map
to descriptive, human-friendly representations.

### Open Issues ###

We actually may not want to support the card and suit representation mappings (see Session
POST response) on the client/coordinator side, in which case it would be on the player
endpoint implementation to map the coordinator representations, if/as needed.

### Endpoints ###

#### Session ####

##### POST - Start a New Session #####

**Request**

- Session Token - string
- Status - string
    - "new"

**Response**

- Card Representation - int[24]
    - 9C, 10C, JC, ... 9D, 10D, JD, ... QS, KS, AS
- Suit Representation - int[4]
    - Clubs, Diamonds, Hearts, Spades

##### PATCH - Notification of Session Status Update #####

**Request**

- Session Token - string
- Status - string
    - "complete" - Session Complete
- Info - string

**Response**

*null*

#### Game ####

##### POST - Start a New Game #####

**Request**

- Session Token - string
- Game - int (0-n)
- Status
    - "new"

**Response**

*null*

##### PATCH - Notification of Game Status Update #####

**Request**

- Session Token - string
- Game - int (0-n)
- Status - string
    - "update" - Score Update
    - "complete" - Game Complete
- Info - string

**Response**

*null*

#### Deal ####

##### POST - Start a New Deal #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Status - string
    - "new"
- Cards - int[24]
    - 0-4  : Position 0 Hand
    - 5-9  : Position 1 Hand
    - 10-14: Position 2 Hand
    - 15-19: Position 3 Hand (dealer)
    - 20   : Turn Card
    - 21-23: Buries
- Position (player) - int (0-3)

**Response**

*null*

##### PATCH - Notification of Deal Status Update #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Status - string
    - "complete" - Deal Complete
- Info - string

**Response**

*null*

#### Bid ####

##### GET - Request for a Bid #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Round - int (0-1)
- Position - int (0-3)

**Response**

- Suit - int (0-3), or -1 = pass
- Alone - boolean

##### POST - Notification of a Bid #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Round - int (0-1)
- Position - int (0-3)
- Suit - int (0-3)
- Alone - boolean

**Response**

- Suggested Suit - int (0-3), or -1 = pass
- Suggested Alone - boolean

#### Swap ####

##### GET - Request for Swap #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Position - int (0-3)
- Swappable Cards - int[5] (0-23, or -1)

**Response**

- Swap Card - int (0-24)

##### POST - Notification of Swap #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Position - int (0-3)
- Swap Card - int (0-24)

**Response**

- Suggested Swap Card - int (0-24)

#### Defense ####

##### GET - Request for a Defense #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Position - int (0-3)

**Response**

- Alone - boolean

##### POST - Notification of a Defense #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Position - int (0-3)
- Alone - boolean

**Response**

- Suggested Alone - boolean

#### Play ####

##### GET - Request for a Card to be Played #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Trick - int (0-4)
- Trick Seq - int (0-3)
- Position (player) - int (0-3)
- Playable Cards - int[5] (0-23, or -1)

**Response**

- Card - int (0-23)

##### POST - Notification of a Card Played #####

**Request**

- Session Token - string
- Game - int (0-n)
- Deal - int (0-n)
- Trick - int (0-4)
- Trick Seq - int (0-3)
- Position (player) - int (0-3)
- Card - int (0-23)

**Response**

- Suggested Card - int (0-23)
