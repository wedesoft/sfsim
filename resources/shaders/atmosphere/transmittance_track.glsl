#version 410 core

uniform sampler2D transmittance;
uniform int height_size;
uniform int elevation_size;

vec2 transmittance_forward(vec3 point, vec3 direction, bool above_horizon);
vec4 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);
bool is_above_horizon(vec3 point, vec3 direction);

// Compute transmittance between two points inside the atmosphere.
vec3 transmittance_track(vec3 p, vec3 q)
{
  return vec3(1, 1, 1);
  vec3 direction;
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    bool above_horizon = is_above_horizon(p, direction);
    vec2 uvp = transmittance_forward(p, direction, above_horizon);
    vec2 uvq = transmittance_forward(q, direction, above_horizon);
    vec3 t1 = interpolate_2d(transmittance, height_size, elevation_size, uvp).rgb;
    vec3 t2 = interpolate_2d(transmittance, height_size, elevation_size, uvq).rgb;
    return t1 / t2;
  } else
    return vec3(1, 1, 1);
}
