# Pre-FRP performance

Before the FRP refactor, the performance was good. Moving a sprite across the screen was smooth.

However, after the refactor the performance is much jerkier and less appealing.

## Pre-FRP profile

I finally did this, and the performance isn't that much better. I saved the profile as `profiles/Timeline-prefrp-93f961ff85d29fca437ed971e5827139ecde140b.json'

There is occaisionally a "long frame", which produces the slightest stutter effect. The `AnimationFrame` is around 1ms on avergage, with occasional 3ms long calls and the rare 5-7ms one (usually in a long frame).

Heap usage is not that different from the inprogress Signals framework, and makes me less worried about my new approach. I think I am at a point I can barge forward, and optimize later.

# Post FRP profiling

I did a few timelines, and the majority of time is spent in the AnimateFrame event fireback.

While there are a few major MB scale GCs, then rarely coincide with the "long frames".

## Lists, Mapping, Allocation

The call stack gets very deep with lazySeq and concat. Apparently my quick-n-dirty but "functional" (meh?) approach of "filter these entities, then grab the other entities, map the first list and concat to the other" was a horrible idea.

I had been meaning to switch to the "map the entities, for each test using appropriate predicate" so we only iterate the list once (instead of [2,3) times) and only allocate one new list (as opposed to 3).

I did this in the `cljs-game.render/render` method, but this did not significantly reduce the call-times.

This is likely the result of the `doall` realization of the lazy list. I needed this here to force the mutative updates of the underlying ThreeJS objects, but I think this also realizes all the other lazy updates.

## Effect of altering the lazy updates

How does changing the expensive ECS filter/map/concat behavior affect the AnimationFrame?

Positively! Doing this just for input and physics dropped the `AnimationFrame` time ~1ms (seems to be 2-5 ms now, averaging 2ms).

However, the stuttering is still present, and the call-graph is still deep.

## Timing

The length of a render actually appears fairly consistent ~3-7ms, but the timing is not consistent--appearing at various points during the frame. The "long frames" are actually those where the `requestAnimationFrame` fired <3 ms towards the end of the frame.

Attempt reducing event usage->call setInterval less and have it tick from there?

## Effect and placement of `doall`

The goal is to shift the burden of realization away from the sensitive AnimationFrame

### How does removing `doall` affect performance? (It will cause rendering to not get updated).

### How does moving `doall` to `step-world` affect performance?

### How does adding a `doall` to `step-world` affect performance?

It extends the time spend in the timer initiated world-step loop from <1ms to 1-3 ms, and deepens the call-graph slightly.

It does make the `AnimationFrame` call-graph much much shallower, but has minimal effect on the call-time. It still varies around 2-7 ms, but there are a small amount of <2 ms outliers I hadn't seen before.

## Reduce number of events.

I am curious if the large event queue from me abusing it is resulting in delayed dispatching of `requestAnimationFrame`. However, event dispatch should be performant and cheap, correct?

It should be cheaper than something I'd create myself. Unless... Javascript event cycles are not optimized for throughput/latency but for accuracy and fairness [at the cost of throughput].

Instead of a timer function that uses `setInterval` for every tick, have a `setInterval` at a longer rate and then count down an accumulated time and triggering the signal each cycle before returning).

## `swap!` versus `reset!`

This made a big difference? I didn't think it would since `swap!` is a small redirection ontop of `reset!`, but it brought the `AnimationFrame` and `Timer` calls down significatly, both of them around 1-2 ms, and both with <1ms times present in non-trivial amounts.

...but it still stutters, and this is unrelated to the loop timing.

Also, the long frame times now coincide with GC times fairly well, occuring close to when the heap reaches peak height.

# Signals
