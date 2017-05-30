# Tiling Engine
Instanced rendering would be the best (and most fun) way to implement this. Support in WebGL is mostly limited to newer browsers and devices, but I like using new things.

ThreeJS also makes this easier to use, and has a great example [here](https://github.com/mrdoob/three.js/blob/dev/examples/webgl_buffergeometry_instancing.html). You make a `InstancedBufferGeometry`, create `InstancedBufferAttribute` from `TypedArrays` of varying length (it appears ThreeJS sets divisor from the relative lengths of attributes).

I should be able to parse a tile description to construct the buffers and make a derivative of the sprite material shader that handles instanced by swaping some of the uniforms for attributes.
    * Should I do this as a new ThreeJS plugin, or a from scratch CLJS function?

I will aim to just to instanced rendering of tiles, but I could use the offscreen render to render a tilemap to a texture and use that single texture.

## Data layout

How would tile data appear?

We need to know what texture to render where

* {:position {:x :y} :atlas :key}
    * what about other textures?
    * How to look up atlas?
* {:position {:x :y} :key}
    * Key is interpreted by system
        * :grass, :grass-bare, :grass-lush, :grass-lushalt

### buffers
1. `positions`  - vertices of sprite (4 x Vector3)
2. `offsets`    - position of tile (n x Vector3)
3. `map`        - texture map to use (n x index)
    3a. `maps`
    3b. `uvs`   - not sure if able to bind more than a single texture? Instead find the uv index in the parent texture
4. `scaleUv`    - in order to use subtextures (see sprite plugin) (n x Vector2)
5. whatever else is needed for subtextured sprites

### Other attributes to add
* Color
* Frames
    * Have time set as a uniform, don't need to update buffers to animate
