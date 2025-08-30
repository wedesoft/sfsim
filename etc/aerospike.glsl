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
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2 * fragCoord.y / iResolution.y - 1.0);
  float left = uv.x;
  float t = iTime;
  uv.y += .01*perlin(t*67.+left)*(.1+left);
  uv.y += .005*perlin(t*101.+left)*(.1+left);
  float period = 10;
  float phase = period * uv.x;
  float min_radius = 0.3;
  float bulge = 0.2;
  float radius = min_radius + bulge * abs(sin(phase));
  float diamond_longitudinal = mod(phase - 0.3 * M_PI, M_PI) - 0.7 * M_PI;
  float inner_radius = radius * 0.9;
  float radial_coord = abs(uv.y);
  float outer_cross_section = sqrt(max(0, radius * radius - radial_coord * radial_coord));
  float inner_cross_section = sqrt(max(0, inner_radius * inner_radius - radial_coord * radial_coord));
  vec3 fringe_color = vec3(0.71, 0.53, 0.66);
  vec3 core_color = vec3(0.58, 0.67, 0.85);
  float outer_plume_strength = 1 - smoothstep(radius - 0.1, radius, abs(uv.y));
  float inner_plume_strength = 1 - smoothstep(inner_radius - 0.1, inner_radius, abs(uv.y));
  vec3 luminocity = fringe_color * outer_cross_section * outer_plume_strength + inner_cross_section * inner_plume_strength * (core_color - fringe_color);
  float diamond_front_length = min_radius / (bulge * period);
  float diamond_back_length = diamond_front_length * 0.7;
  float tail_start = 0.3 * diamond_front_length;
  float tail_length = 0.8 * diamond_front_length;
  float diamond_length = diamond_longitudinal > 0.0 ? diamond_back_length : diamond_front_length;
  float diamond_radius = min_radius * max(0, 1.0 - abs(diamond_longitudinal / period) / diamond_length);
  float diamond_strength = min(1.0, max(0, 1.0 - (tail_start + diamond_longitudinal / period) / tail_length));
  diamond_strength = diamond_strength * (1 - smoothstep(diamond_radius - 0.1, diamond_radius, abs(uv.y)));
  float diamond_cross_section = sqrt(max(0, diamond_radius * diamond_radius - radial_coord * radial_coord));
  float diamond = 1.5 * diamond_cross_section * diamond_strength;
  vec3 background = 0.4 * vec3(0.39, 0.39, 0.40);
  vec3 diamond_color = diamond * vec3(0.98, 0.93, 1.0);
  vec3 color = background + luminocity + diamond_color;
  fragColor = vec4(color, 1);
}
