# Postmortem
## Introduction
I was ramping up work on my CLJS based game-engine in preparation for this game jam, and thought it'd be a great chance to see how far along it had come and what things were missing. Between a release deadline at work, recently moving to a new apartment, and other personal things happening, I had a late start and was unable to dedicate as much time/energy at the jam as I had wished. While I did not have a completed game, I did end up with a small tech-demo showing the progress of my skills and engine.

## Engine Design
I am currently targeting CLJS/WebGL using Three.js. CLJS/WebGL was meant as a fun way to keep my JS/Web skills developed as I do a lot of Java for my day job, and Three.js was a great way to get up and running quickly.

I wanted the design (or at least the user-code) to favor the functional and immutable programming paradigm of Clojure. I have been trying to write games since I was in high school, and had done enough work in Clojure and functional languages up until this point that I was comfortable enough writing a game-engine in this style. This was going well until I hit the async nature of JS, and had the first paradigm mismatch that led to implementation difficulties and headaches and compromises.

However, what is great about Clojure is the ability to break out of the language and make those compromises--the only limiting factor was how stubborn and strict I was willing to be.

## Concept
I wrote a short introduction [*The Hunt Begins*](https://github.com/sctincman/cljs-game/blob/master/doc/Design-1stDemo/Design.md) for a game idea I was using as a target for my first prototype. The idea was to keep things simple and focus on aethestics and atmosphere.

The setting would be a moonlit forest, centered on a deer wearing a wolf's pelt. The player could move the character left/right and interact with the scenery and characters. The player could choose to follow a pack of wolves as they hunt, or retreat and attempt to catch up to a herd of deer. The players actions would determine which of a small number of ending scenes would play out.

I wanted at a bare minimum to have a cohesive look, animations, movement, and parallax scrolling.

## Prologue
By the time the jam started, the big 3 things I needed that I hadn't implemented yet included

1. [Working] Collision Detection
2. Animation
3. Sound

Being more of a visual/mechanics person, sound was less of a priority, but I absolutely wanted animation and collision detection. I also had almost no tooling done, but given the scope I didn't think this was necessary, and could fit in manual declaration. I also had no assets to use, but knew I'd be able to search various places for public domain and free-to-use assets.

I needed collision detection, and had an overcomplicated and non-working system in place, which would make this a good place to start.

## Collision detection
Because I wanted this engine to be general use, and eventually capable of an excellent platformer, I had started collision detection to resemble Chipmunk 2D's algorithms and layout. I currently had only implemented axis-aligned bounding boxes with spatial hash buckets for quick culling and boolean detection ("colliding or not" vs "center of collision"). My first [delayed] day of the game-jam was spent debugging and finishing this to be usable.

This was a huge time/energy/brain sink for something that didn't make it into the final demo, and was overkill for the design spec. I am extremely glad I was already very familiar with collision detection, or this would have been a nightmare.

## Aesthetics and assets
 I spent an entire day working on finding/modifying assets and making some mock layouts to envision how the game would look. To fit a moonlit forest, potentially with occasional wild-life/fires, I decided to restrict myself to a split complimentary color-scheme using purple, green, and vermilion. I ended up using the [Parallalx Forest](https://opengameart.org/content/forest-background) asset for a background and to test out the color scheme on. While dialing in the green for foreground was difficult, the purple worked extremely well for a dimly lit nighttime background. I also grabbed a [deer sprite sheet](https://opengameart.org/content/deer) and adjusted the color to be more vermillion, which worked out well.

 With these assets in hand, I made a mock image of how I envisioned the game looking. This was a great exercise to see if the assets all worked together and had a cohesive aesthetic, and if the color scheme strengthened the atmosphere.

While I was anxious spending a full day working on this, having better graphics to use while developing made further development more enjoyable. This was also a nice day to do this, as it was an extremely draining day at my day job, and playing with sprites, graphics, and color schemes was more relaxing and less draining than trying to frantically program a major subsystem.

## Parallax
I already had the ability to toggle perspective/orthographic mode in my engine, and intended to implement the game in orthographic mode. This then required me to implement parallax scrolling. I started to try and work this out, but then decided it was too much work and indirection. As I was using a 3D engine to begin with, I quickly played with using the perspective camera and moving things along the z-axis to get a decent amount of apparent depth. After some quick iteration of values/scale, I had a good looking 2D/3D appearance I was happy with.

This is a point where I almost fell into a trap of over-engineering things. Yes, orthographic would likely end up looking better in a 2D game, and if I decided to go that route I could have made a quick-and-dirty parallax scrolling, but it was too tempting to make that subsystem as versatile and robust as possible. Leaving things in 3D saved me a lot of time and effort.

## Sprites: Textures and Atlases
Surprisingly, I was able to very quickly implement texture and texture atlas wrappers. Using Three.js's `SpriteMaterial` with my texture atlas implementation made for a fairly versatile way to implement sprite-sheets. This was a very big step towards adding animations.

The big problem I was facing with this engine (and a problem I had kept putting off) was how to handle asynchronous texture loading due to using JS/Three.js. Any solutions I tried using closures/futures/promises all had problems due to the cooperative nature of JS concurrent/async programming.

However, I finally had a breakthrough: preloading. I reworked the game initialization code to preload all the textures, then used callback chains to call the `start-game` function when they were fully loaded, passing in the list of texture objects.

This does make for a very slow start when loading remotely, and this does assume that all the game's assets are capable of being loaded all at once, which won't scale to larger games, but this was definitely a shortcut I could afford to take, and still made a very clean entry point.

I wanted a tiling engine in order to at least have the background repeat itself. Three.js does not natively support instanced rendering, and would require me to have an object per thing to render. If I went for a tile-based game this would have brought my engine to a crawl. While having an object per larger background image would work, I wanted some variety in my environment, and worried a kludge here would make the game unplayable.

## Animations
This is something I absolutely wanted before the deadline, and something I was working on until the last minute.

I ended up using my `Signal` interface, making an animation a reduction over a time signal. This took some up-front brain power to figure out, but felt very natural once it was done. However, this was a uniform time-signal, forcing me to fake variable frames by repeating animation frames.

I did not end-up with "state based animation" by the deadline, but this is something I was able to add the day after by implementing a new signal procedure: `switch`.

However, I received a great amount of satisfaction from animating the deer: figuring out how long to hold a frame, repeat frames, and cycle back.

## Deployment
I was extremely happy `itch.io` allows one to embed HTML games using a zip-file with an `index.html`. However, I had not put any time or thought into a deployment strategy for this game/engine and frantically tried to fix my "main" cljsbuild configuration. This did not include the advanced compilation strategy (which three.js is not compatible with), which resulted in a large slow archive.

Figuring out how to do this put me ~15 minutes over the deadline and caused me to miss the submission window.

## Overall
The final game has a single multi-layered background, with a few animated deer in the scene. One is moving left-right (but stays in a right-only animation), and the player can move a deer left and right (who is similarly stuck in a moving-right animation).

This wasn't even close to the limited demo I had envisioned, even after much much culling in design. The lack of any sort of mechanics doesn't even make this a game. I was disappointed I wasn't able to get to even a MVP. I knew the tight time-constraints of a jam would force me to take shortcuts and compromises, and tried to adjust my workflow appropriately. However, as this was my home-grown engine that I had grandiose plans for, I was still preoccupied with how to properly engineer it.

Overall though, the deadline of the jam did force me to make some shortcuts and focus on getting something done, which resulted in a huge amount of work being done.

## Lessons Learned

### Deployment is important
This is something I should have already had figured out, and not something to put off until the end. This would have allowed me to actually make the deadline and possibly had a better zip file deployed if I could have figured out to get the advanced compilation mode of Closure working. However, I think it is worth mentioning that the excellent tooling for Clojure and Clojurescript (specifically lein and figwheel), was a reason this was overlooked for so long in my project.

### Use familiar and established tooling?
The next lessons I think would be "use a completed/well-established engine, use tooling for asset creation, and know your tooling." I could have very quickly iterated on ideas/mechanics/aesthetics and had a more fleshed-out game had I not focused on technical and engineering problems of using an in-progress, prototypical, and home-grown engine full of NIH syndrome. However! This is a language-focused game-jam, and the reason I was attracted to this was the technical edge using language like Clojure would give me. Looking at the code I wrote during this game jam, there is very little I am going to throw out--which is more than I can say for the code I've written in more imperative languages.

I am looking forward to the next jam, and will be working on my engine in preparation.
