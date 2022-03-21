#version 410 core

vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int size,
                         float power, bool sky, bool ground);
vec4 interpolate_4d(sampler2D table, int size, vec4 idx);
vec3 transmittance_track(sampler2D transmittance, float radius, float max_height, int size, float power, vec3 p, vec3 q);

vec3 ray_scatter_track(sampler2D ray_scatter, sampler2D transmittance, float radius, float max_height, int size, float power,
                       vec3 light_direction, vec3 p, vec3 q)
{
  vec3 direction;
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    vec4 ray_scatter_index_p = ray_scatter_forward(p, direction, light_direction, radius, max_height, size, power, false, false);
    vec3 ray_scatter_p = interpolate_4d(ray_scatter, size, ray_scatter_index_p).rgb;
    vec4 ray_scatter_index_q = ray_scatter_forward(q, direction, light_direction, radius, max_height, size, power, false, false);
    vec3 ray_scatter_q = interpolate_4d(ray_scatter, size, ray_scatter_index_q).rgb;
    vec3 transmittance_p_q = transmittance_track(transmittance, radius, max_height, size, power, p, q);
    return ray_scatter_p - transmittance_p_q * ray_scatter_q;
  } else
    return vec3(0, 0, 0);
}
