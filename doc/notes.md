# Notes

Project developments notes and logs.

# Inversion of Control

Clojurescript is missing the concurrent primitives I wanted to use to isolate browser and Javascript control, allowing me to invert the inversion-of-control, so I could avoid mutating global state. The cooperative nature of the single-threaded engine is preventing me from doing so easily without breaking away further from idiomatic Clojure. There is core.async, but I do not grok it's paradigm and philosphoy.

## FRP

I've realized I need to aggressively implement Function Reactive Programming in this case, in particular I should focus on a push variant, where before I was trying to force a pull paradigm.

I want to avoid mutating global state, so how best to organize this "push" FRP paradigm?

## Events

Javascript is heavily async and event oriented, and I'm best off not fighting it further. I'm going instead to exploit it using CustomEvents, using the following possible ideas.

### Dispatcher

Entities will have behaviors that define their subscribe to events with their parent, called the dispatcher. The dispatcher will addEventListener, and the CustomEvent detail will contain the identifier of the target. The dispatcher will receive the event, iterate it's child entities, call the behavior registered for the event. The behavior will modify the entity, and return the new entity for the dispatcher.

This would be nice as you could make a transducer to map the entities, returning the new updated ones (w/o mutation!).

The downsides would be the indirection of a dispatcher, and the need to iterate the entities for each event.

### Subscribe and Publish

Have objects subscribe to CustomEvents. This can either be generic with a target field, or specific (will need to see how well the EventHandlers scale). Either way the handler will call a behavior directly, without the need to iterate the entities.

The downside is how to update an entity without mutating a global variable. One possible solution would be to have the behavior update the entity and then emit a CustomEvent of the type "update-entity" and, complete with a identifier and update function. More on this in the next section

### Obervables, Behaviors, and no returning.

Instead of functions that update an entity and return the new entity, have behaviors emit an event with a function to update that entity.

The world object can be a listener for these events, and consumer them as a stream, reducing over them. The need for an update function instead of a new updated-entity is to prevent undoing previous behaviors.

For example: an object collides on the player's right side damaging the player. The event "damage player" is emitted with the player's health decremented.

Before that event is consumed, and the entity updated, an input event fires, which triggers the "move player entity right" event. The behavior takes the player entity, increments the position, then fires off the updated entity. However, this is the old player entity without the damaged health. As the world reduces and replaces the entity, the player's health is reduced, but then replaced with an undamaged version that is moved to the right.

However, if the result is a function that updates the player, the first event will decrement the health, then the "move right" event will take that updated player and move it right. The functions should be pure in the case I decide to use Atoms and the update function needs to be reapplied.

### Collect and Compose

A possible optimization (see LightTable), by having an entity introspect and trigger it's own behaviors, collecting the functions that operate on itself. Then, compose these functions and place in the "update entity" event.

See below, have the update behaviors also accept a time-step?

## World state

### Objects

Associative where key is the id (for fast lookups), and the value is the entity. World updates will be a reduction of the entities and the event stream.

(reduce consume-event world events)

### Time-step

I think the best way to do this is to have a "propogate" event that has a ":target :all" and delta-t.

That seems messy, perhaps each event can be tagged with the time it was emitted, and then also the time it was processed?

## Input and FSM

How do we model behavior with signals?

button input arrives, move from standing to running

Input ->
Entity -> movement system -> Entity (velocity set)

(cyclic procedures? check how CLJs does watches)

# ECS and Signals
Systems are signal driven, state and data stored in entity.

Components cannot update other components, but must trigger signals (no need to worry about a large mutable state between systems, and no need to worry how to thread a immutable object through spaghetti code)

Component and Entity implement signals?
