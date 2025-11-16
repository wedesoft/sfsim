#define START <%= plume-start %>
#define SPIKE -11.18
#define ENGINE_SIZE (START - SPIKE)
#define END <%= plume-end %>
#define WIDTH2 <%= plume-width-2 %>
#define RADIUS 3.9011
#define LAYER 0.6
#define BASE_DENSITY 2.0
#define SPEED 50.0
#define DIAMOND_STRENGTH 0.2
#define DIAMOND_DENSITY 5.0

vec2 cylinder1_base = vec2(-10.9544,  4.3511);
vec2 cylinder2_base = vec2(-10.9544, -4.3511);

uniform float pressure;
uniform float nozzle;
uniform float min_limit;
uniform float time;

float limit(float pressure);
float bulge(float pressure, float x);
float sdf_circle(vec2 point, vec2 center, float radius);
float sdf_rectangle(vec2 point, vec2 rectangle_min, vec2 rectangle_max);
float noise3d(vec3 coordinates);
float diamond(float pressure, vec2 uv);

vec2 envelope(float pressure, float x) {
  float bulge = bulge(pressure, x);
  return vec2(bulge + WIDTH2 - nozzle, bulge);
}

float sdf_engine(vec2 cylinder1_base, vec2 cylinder2_base, vec3 p) {
  if (abs(p.z) > WIDTH2) {
    return abs(p.z) - WIDTH2;
  }
  if (abs(p.y) <= nozzle) {
    vec2 base = p.y > 0.0 ? cylinder1_base : cylinder2_base;
    return RADIUS - LAYER - length(p.xy - base);
  }
  vec2 o = vec2(min(p.x - (cylinder1_base.x - RADIUS + LAYER), p.x), nozzle);
  return length(o - vec2(p.x, abs(p.y)));
}

vec4 plume_transfer(vec3 point, float plume_step, vec4 plume_scatter)
{
  float transition = clamp((limit(pressure) - min_limit) / (nozzle - min_limit), 0.0, 1.0);
  vec2 envelope = envelope(pressure, START - point.x - mix(ENGINE_SIZE, 0.0, transition));
  float engine_pos = clamp((START - point.x) / ENGINE_SIZE, 0.0, 1.0);
  float radius = 0.5 * (envelope.x + envelope.y);
  float base_sdf = sdf_engine(cylinder1_base, cylinder2_base, point);
  float fade = clamp((point.x - END) / (START - END), 0.0, 1.0);
  float shape_mix = mix(sdf_circle(point.zy, vec2(0, 0), radius), sdf_rectangle(point.zy, -envelope, +envelope), fade);
  float sdf = mix(base_sdf, shape_mix, mix(engine_pos, 1.0, transition));
  if (sdf < 0.0) {
    float dz = mix(WIDTH2, envelope.x, engine_pos);
    float dy = mix(LAYER, envelope.y, engine_pos);
    float density = BASE_DENSITY / (dz * dy) * fade;
    float fringe = max(1.0 + sdf / 1.0, 0.0);
    vec3 scale = 2.0 * vec3(0.1, nozzle / envelope.y, nozzle / envelope.x);
    float attenuation = 0.7 + 0.3 * noise3d(point * scale + time * vec3(SPEED, 0.0, 0.0));
    vec3 flame_color = mix(vec3(0.6, 0.6, 1.0), mix(vec3(0.90, 0.59, 0.80), vec3(0.50, 0.50, 1.00), fringe), pressure);
    float diamond = mix(DIAMOND_STRENGTH, diamond(pressure, vec2(START - point.x - mix(ENGINE_SIZE, 0.0, transition), max(0.0, sdf + dy))), mix(engine_pos, 1.0, transition));
    float plume_transmittance = exp(-density * plume_step);
    plume_scatter.rgb += flame_color * plume_step * density * attenuation * plume_scatter.a;
    plume_scatter.rgb += DIAMOND_DENSITY * diamond * density * plume_step * attenuation * plume_scatter.a;
    plume_scatter.a *= plume_transmittance;
  };
  return plume_scatter;
}
