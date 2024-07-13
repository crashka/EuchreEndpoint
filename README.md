# Euchre Endpoint

## Project Overview

This project is composed of two parts:

- The API specification for invoking Java-based algorithms from the `euchre-plt`
  tournament framework (see [euchre-plt](https://github.com/crashka/euchre-plt))
- A Spring Boot-based implementation of the API, which binds to the `EuchreBeta` engine
  (see [EuchreBeta](https://github.com/crashka/EuchreBeta))

### API Specification

The server side only plays hands for individual games.  The client side (which we will
call the "coordinator") manages the larger context of deals and accounting for matches,
tournaments, etc.

### Endpoint Implementation


## Project Status


## Related Projects

[EuchreEndpoint2](https://github.com/crashka/EuchreEndpoint2) – a tighter, next-generation
version of the current API (and server implementation?) that creates separate sessions for
individual players (i.e. eliminates the "shadow server" model), recognizes persistent
player identities, and provides explicit game, match, tournament, and Elo rating
information for more advanced, context-based strategies

## License

This project is licensed under the terms of the MIT License.
