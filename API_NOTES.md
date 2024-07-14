# API Notes

## Overview

Note that sequential data (e.g. counters and lists) are all zero-based.  For UI/output
interfaces, we may want to display "game number", "deal number", and "trick number" as
N+1.  For other internal indexable things (e.g. card, position, suit, etc.), we will map
to descriptive, human-friendly representations.

## Interface Issues/Discussion

We actually may not want to support the card and suit representation mappings (see Session
POST response) on the client/coordinator side, in which case it would be on the player
endpoint implementation to map the coordinator representations, if/as needed.

Note that the current interface does not support keeping track of game scores, since there
is no representation of table positions across deals (only dealer/bidding positions within
a deal).  We will probably not take this on, since the current design is very specific to
support for [EuchreBeta](https://github.com/crashka/EuchreBeta)—rather, we'll leave this
to be solved by [EuchreEndpoint2](https://github.com/crashka/EuchreEndpoint2).

## Endpoints

### Session

#### POST - New session notification

**Request**

- Session Token - string
- Status - string
    - "new" - New session

**Response**

\[Session Protocol]

- Card Representation - int[24]
    - 9C, 10C, JC, ... 9D, 10D, JD, ... QS, KS, AS
- Suit Representation - int[4]
    - Clubs, Diamonds, Hearts, Spades

#### PATCH - Session update or completion notification

**Request**

- Session Token - string
- Status - string
    - "update" - (see Info string)
    - "complete" - Session complete
- Info - string

**Response**

\[Local Session Status]

- Status
    - "active" - Session active
    - "complete" - Session complete

### Game

#### POST - New game notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Status
    - "new" - New game

**Response**

\[Local Game Status]

- Status
    - "active" - Game created locally

#### PATCH - Game update or completion notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Status - string
    - "update" - (see Info string)
    - "complete" - Game complete
- Info - string

**Response**

\[Local Game Status]

- Status
    - "active" - Game active
    - "complete" - Game complete

### Deal

#### POST - New deal notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
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

\[Local Deal Status]

- Status
    - "active" - Deal created locally

#### PATCH - Deal update or completion notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Status - string
    - "complete" - Deal Complete
- Info - string (deal winner and number of points awarded)

**Response**

\[Local Deal Status]

- Status
    - "active" - Deal active
    - "complete" - Deal complete

### Bid

#### GET - Bid request

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Round - int (0-1)
- Turn Card - int (0-23)
- Position (bidding) - int (0-3)

**Response**

- Suit - int (0-3), or -1 = pass
- Alone - boolean

#### POST - Bid notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Round - int (0-1)
- Turn Card - int (0-23)
- Position (bidding) - int (0-3)
- Suit - int (0-3), or -1 = pass
- Alone - boolean

**Response**

- Suggested Suit - int (0-3), or -1 = pass
- Suggested Alone - boolean

### Swap

#### GET - Swap request

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Declarer Pos - int (0-3)
- Turn Card - int (0-23)
- Position (swapping) - int (always 3)
- Swappable Cards - int[5] (0-23)

**Response**

- Swap Card - int (0-23), or-1 = no swap

#### POST - Swap notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Declarer Pos - int (0-3)
- Turn Card - int (0-23)
- Position (swapping) - int (always 3)
- Swap Card - int (0-23)

**Response**

- Suggested Swap Card - int (0-23)

### Defense

#### GET - Defense request

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Declarer Pos - int (0-3)
- Trump Suit - int (0-3)
- Position (defending) - int (0-3)

**Response**

- Alone - boolean

#### POST - Defense notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Declarer Pos - int (0-3)
- Trump Suit - int (0-3)
- Position (defending) - int (0-3)
- Alone - boolean

**Response**

- Suggested Alone - boolean

### Trick

#### POST - New trick notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Trick Num - int (0-4)
- Status - string
    - "new"
- Lead Position - int (0-3)

**Response**

\[Local Trick Status]

- Status
    - "active" - Trick created locally

#### PATCH - Trick update or completion notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Trick Num - int (0-n)
- Status - string
    - "complete" - Trick Complete
- Info - string (trick winner)

**Response**

\[Local Trick Status]

- Status
    - "active" - Trick active
    - "complete" - Trick complete

### Play

#### GET - Play request

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Trick Num - int (0-4)
- Trick Seq - int (0-3)
- Position (playing) - int (0-3)
- Playable Cards - int[5] (0-23, or -1)

**Response**

- Card - int (0-23)

#### POST - Play notification

**Request**

- Session Token - string
- Game Num - int (0-n)
- Deal Num - int (0-n)
- Trick Num - int (0-4)
- Trick Seq - int (0-3)
- Position (playing) - int (0-3)
- Card - int (0-23)

**Response**

- Suggested Card - int (0-23)
