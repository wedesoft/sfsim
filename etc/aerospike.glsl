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

float hash21(vec2 v) {
    return fract(sin(dot(v, vec2(12.9898, 78.233))) * 43758.5453123);
}

// https://www.shadertoy.com/view/WsjGRz
float noise(vec2 uv) {
	vec2 f = fract(uv);
	vec2 i = floor(uv);
	f = f * f * (3. - 2. * f);
	return mix(
		mix(hash21(i), hash21(i + vec2(1,0)), f.x),
		mix(hash21(i + vec2(0,1)), hash21(i + vec2(1,1)), f.x), f.y);
}


float fbm(vec2 n) {
  float total = 0.0, amplitude = 0.5;
  for (int i = 0; i < 3; i++) {
    total += noise(n) * amplitude;
    n = n * 2.0;
    amplitude *= 0.5;
  }
  return total;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2.0 * fragCoord.y / iResolution.y - 1.0);
  float left = uv.x;
  float t = iTime;
  uv.y += .01*perlin(t*67.+left)*(.1+left);
  uv.y += .005*perlin(t*101.+left)*(.1+left);
  float period = 15.0;
  float phase = period * uv.x;
  float min_radius = 0.2;
  float bulge = 0.1;
  float radius = min_radius + bulge * abs(sin(phase));
  float diamond_longitudinal = mod(phase - 0.3 * M_PI, M_PI) - 0.7 * M_PI;
  float inner_radius = radius * 0.9;
  float radial_coord = abs(uv.y);
  float outer_cross_section = sqrt(max(0.0, radius * radius - radial_coord * radial_coord));
  vec3 background = vec3(0.12, 0.27, 0.42);
  vec3 fringe_color = vec3(0.78, 0.63, 0.74);
  float smoothing = 0.03;
  float flame_frequency_longitudinal = 20.0;
  float flame_frequency_lateral = 40.0;
  float brightness = fbm(vec2(uv.x * flame_frequency_longitudinal - t * 200.0, uv.y * flame_frequency_lateral * min_radius / radius));
  brightness = clamp(brightness, 0.0, 1.0);
  float variation = 0.08;
  outer_cross_section += brightness * variation - variation * 0.5;
  float strength = 2.0 * (1.0 - smoothstep(radius - smoothing, radius, abs(uv.y)));
  vec3 luminocity = strength * max(0.0, outer_cross_section) * (fringe_color - background);
  float diamond_front_length = min_radius / (bulge * period);
  float diamond_back_length = diamond_front_length * 0.7;
  float tail_start = 0.3 * diamond_front_length;
  float tail_length = 0.8 * diamond_front_length;
  float diamond_length = diamond_longitudinal > 0.0 ? diamond_back_length : diamond_front_length;
  float diamond_radius = min_radius * max(0.0, 1.0 - abs(diamond_longitudinal / period) / diamond_length);
  float diamond_strength = min(1.0, max(0.0, 1.0 - (tail_start + diamond_longitudinal / period) / tail_length));
  diamond_strength = diamond_strength * (1.0 - smoothstep(diamond_radius - 0.1, diamond_radius, abs(uv.y)));
  float diamond_cross_section = sqrt(max(0.0, diamond_radius * diamond_radius - radial_coord * radial_coord));
  float diamond = 1.5 * diamond_cross_section * diamond_strength;
  vec3 diamond_color = diamond * vec3(0.98, 0.93, 1.0);
  vec3 color = background + luminocity + diamond_color;
  fragColor = vec4(color, 1);
}
