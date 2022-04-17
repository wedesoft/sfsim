#version 410 core

vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int size,
                         float power, bool above_horizon);
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx);
vec3 transmittance_track(sampler2D transmittance, float radius, float max_height, int height_size, int elevation_size,
                         float power, vec3 p, vec3 q);
bool sky_or_ground(float radius, vec3 point, vec3 direction);

vec3 ray_scatter_track(sampler2D ray_scatter, sampler2D transmittance, float radius, float max_height, int size, float power,
                       vec3 light_direction, vec3 p, vec3 q)
{
  vec3 direction;
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    bool above_horizon = sky_or_ground(radius, p, direction);
    vec4 ray_scatter_index_p = ray_scatter_forward(p, direction, light_direction, radius, max_height, size, power,
                                                   above_horizon);
    vec3 ray_scatter_p = interpolate_4d(ray_scatter, size, size, size, size, ray_scatter_index_p).rgb;// TOOD: use proper shape
    vec4 ray_scatter_index_q = ray_scatter_forward(q, direction, light_direction, radius, max_height, size, power,
                                                   above_horizon);
    vec3 ray_scatter_q = interpolate_4d(ray_scatter, size, size, size, size, ray_scatter_index_q).rgb;// TODO: use proper shape
    vec3 transmittance_p_q = transmittance_track(transmittance, radius, max_height, size, size, power, p, q);// TODO: pass different size values
    return ray_scatter_p - transmittance_p_q * ray_scatter_q;
  } else
    return vec3(0, 0, 0);
}
