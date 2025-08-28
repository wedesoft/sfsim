#version 410 core

#define M_PI 3.1415926535897932384626433832795
#define HASHSCALE 0.1031

uniform vec2 iResolution;
uniform float iTime;

float hash(float p)
{
  vec3 p3  = fract(vec3(p) * HASHSCALE);
  p3 += dot(p3, p3.yzx + 19.19);
  return fract((p3.x + p3.y) * p3.z);
}

float hash31( vec3 p )
{
  float h = dot(p,vec3(17, 1527, 113));
  return fract(sin(h)*43758.5453123);
}


float fade(float t) { return t*t*t*(t*(6.*t-15.)+10.); }

float grad(float hash, float p)
{
  int i = int(1e4*hash);
  return (i & 1) == 0 ? p : -p;
}

float perlin(float p)
{
  float pi = floor(p), pf = p - pi, w = fade(pf);
  return mix(grad(hash(pi), pf), grad(hash(pi + 1.0), pf - 1.0), w) * 2.0;
}

float sigmoid(float x)
{
  return 1.0 / (1.0 + exp(-x));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  vec2 uv;
  uv.x = fragCoord.x / iResolution.x;
  float left = uv.x;
  float t = iTime;
  uv.y = 2 * fragCoord.y / iResolution.y - 1.0;
  uv += .03*perlin(t*.5+left)*(.5+left);
  uv += .01*perlin(t*7.+left)*(.5+left);
  uv.y += .01*perlin(t*67.+left)*(.5+left);
  uv.y += .005*perlin(t*101.+left)*(.5+left);
  uv.x += .02*(.2-abs(uv.y))*perlin(t*3.);
  float phase = 10 * uv.x;
  float min_radius = 0.3;
  float radius = min_radius + 0.15 * abs(sin(phase));
  float w = mod(phase - 0.5 * M_PI, M_PI) - 0.5 * M_PI;
  float inner_radius = radius * 0.8;
  float dy = abs(uv.y) / radius;
  float dx = sqrt(max(0, 1 - dy * dy));
  float dy2 = abs(uv.y) / inner_radius;
  float dx2 = sqrt(max(0, 1 - dy2 * dy2));
  float luminocity = 1.0 * dx - 0.8 * dx2;
  // float diamond = max(0.0, (1 - abs(w) / 0.1)) * max(0.0, (1 - abs(y) / radius));
  float diamond_radius = 0.0;
  float diamond_strength = 0.0;
  if (w > -0.4) {
    if (w < 0.0) {
      diamond_strength = smoothstep(-0.4, -0.2, w);
      diamond_radius = min_radius + w / 0.4 * min_radius * 0.3;
    } else {
      diamond_strength = 1.0;
      diamond_radius = max(min_radius - w * 0.5, 0.0);
    };
  };
  float diamond = 0.0;
  if (diamond_radius > 0) {
    diamond_strength = diamond_strength * (1 - smoothstep(diamond_radius - 0.1, diamond_radius, abs(uv.y)));
    float dy = abs(uv.y) / diamond_radius;
    float dx = sqrt(max(0, 1 - dy * dy));
    diamond = 0.4 * dx * diamond_strength;
  };
  vec3 color = vec3(0.60, 0.62, 0.63) * (1.0 - luminocity - diamond) + vec3(0.77, 0.75, 0.76) * luminocity + vec3(0.90, 0.75, 0.71) * diamond;
  fragColor = vec4(color, 1);
}
