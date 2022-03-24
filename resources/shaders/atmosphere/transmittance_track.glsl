#version 410 core

vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int size, float power, bool sky,
                           bool ground);
vec4 interpolate_2d(sampler2D table, int size, vec2 idx);
bool sky_or_ground(float radius, vec3 point, vec3 direction);

vec3 transmittance_track(sampler2D transmittance, float radius, float max_height, int size, float power, vec3 p, vec3 q)
{
  vec3 direction;
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    bool sky = sky_or_ground(radius, p, direction);
    vec2 uvp = transmittance_forward(p, direction, radius, max_height, size, power, sky, !sky);
    vec2 uvq = transmittance_forward(q, direction, radius, max_height, size, power, sky, !sky);
    vec3 t1 = interpolate_2d(transmittance, size, uvp).rgb;
    vec3 t2 = interpolate_2d(transmittance, size, uvq).rgb;
    return t1 / t2;
  } else
    return vec3(1, 1, 1);
}
