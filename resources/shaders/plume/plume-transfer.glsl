#define START 0.0
#define SPIKE -3.6753
#define ENGINE_SIZE (START - SPIKE)
#define END <%= plume-end %>
#define WIDTH2 <%= plume-width-2 %>
#define RADIUS 3.9011
#define LAYER 0.6
#define BASE_DENSITY 5.0
#define SPEED 50.0
#define DIAMOND_DENSITY 7.0

// References:
// Mach diamonds: https://www.shadertoy.com/view/WdGBDc
// flame thrower: https://www.shadertoy.com/view/XsVSDW
// fbm and domain warping: https://www.shadertoy.com/view/WsjGRz
// 3D perlin noise: https://www.shadertoy.com/view/4djXzz

// Standalone prototype shader:
// https://www.shadertoy.com/view/t3XyzB

vec2 cylinder1_base = vec2(-3.4497,  4.3511);
vec2 cylinder2_base = vec2(-3.4497, -4.3511);

uniform float pressure;
uniform float plume_nozzle;
uniform float min_limit;
uniform float diamond_strength;
uniform float time;
uniform float plume_throttle;

float plume_limit(float pressure);
float plume_bulge(float pressure, float x);
float sdf_circle(vec2 point, vec2 center, float radius);
float sdf_rectangle(vec2 point, vec2 rectangle_min, vec2 rectangle_max);
float noise3d(vec3 coordinates);
float diamond(float pressure, vec2 uv);

vec2 envelope(float pressure, float x, float x_constrained) {
  float bulge_constrained = plume_bulge(pressure, max(0.0, x_constrained));
  float bulge = plume_bulge(pressure, x);
  return vec2(bulge_constrained + WIDTH2 - plume_nozzle, bulge);
}

float sdf_engine(vec2 cylinder1_base, vec2 cylinder2_base, vec3 p) {
  if (abs(p.y) > WIDTH2) {
    return abs(p.y) - WIDTH2;
  }
  if (abs(p.z) <= plume_nozzle) {
    vec2 base = p.z > 0.0 ? cylinder1_base : cylinder2_base;
    return RADIUS - LAYER - length(p.xz - base);
  }
  vec2 o = vec2(min(p.x - (cylinder1_base.x - RADIUS + LAYER), p.x), plume_nozzle);
  return length(o - vec2(p.x, abs(p.z)));
}

vec4 plume_transfer(vec3 point, float plume_step, vec4 plume_scatter)
{
  if (plume_throttle > 0.0) {
    float transition = clamp((plume_limit(pressure) - min_limit) / (plume_nozzle - min_limit), 0.0, 1.0);
    vec2 envelope = envelope(pressure, START - point.x - mix(ENGINE_SIZE, 0.0, transition), START - point.x - ENGINE_SIZE);
    float engine_pos = clamp((START - point.x) / ENGINE_SIZE, 0.0, 1.0);
    float radius = 0.5 * (envelope.x + envelope.y);
    float base_sdf = sdf_engine(cylinder1_base, cylinder2_base, point.xzy);
    float shape_transition = clamp((point.x - END) / (START - END), 0.0, 1.0);
    float shape_mix = mix(sdf_circle(point.zy, vec2(0, 0), radius), sdf_rectangle(point.zy, -envelope, +envelope), shape_transition);
    float sdf = mix(base_sdf, shape_mix, mix(engine_pos, 1.0, transition));
    if (sdf < 0.0) {
      float dz = mix(WIDTH2, envelope.x, engine_pos);
      float dy = mix(LAYER, envelope.y, engine_pos);
      float end = mix(START, END, plume_throttle);
      float fade = clamp((point.x - end) / (START - end), 0.0, 1.0);
      float density = BASE_DENSITY / (dz * dy) * fade;
      float fringe = max(1.0 + sdf / 1.0, 0.0);
      vec3 scale = 2.0 * vec3(0.1, plume_nozzle / envelope.x, plume_nozzle / envelope.y);
      float attenuation = 0.7 + 0.3 * noise3d(point.xzy * scale + time * vec3(SPEED, 0.0, 0.0));
      vec3 flame_color = mix(vec3(0.6, 0.6, 1.0), mix(vec3(0.90, 0.59, 0.80), vec3(0.50, 0.50, 1.00), fringe), pressure);
      float diamond = mix(diamond_strength, diamond(pressure, vec2(START - point.x - mix(ENGINE_SIZE, 0.0, transition), max(0.0, sdf + dy))), mix(engine_pos, 1.0, transition));
      float plume_transmittance = exp(-density * plume_step);
      plume_scatter.rgb += flame_color * plume_step * density * attenuation * plume_scatter.a;
      plume_scatter.rgb += DIAMOND_DENSITY * diamond * density * plume_step * attenuation * plume_scatter.a;
      plume_scatter.a *= plume_transmittance;
    };
  };
  return plume_scatter;
}
