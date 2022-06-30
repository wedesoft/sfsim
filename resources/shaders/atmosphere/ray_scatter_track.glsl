#version 410 core

uniform sampler2D ray_scatter;
uniform int height_size;
uniform int elevation_size;
uniform int light_elevation_size;
uniform int heading_size;

vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, bool above_horizon);
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx);
vec3 transmittance_track(vec3 p, vec3 q);
bool is_above_horizon(vec3 point, vec3 direction);

// Compute in-scattered light between two points inside the atmosphere.
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q)
{
  vec3 direction;
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    bool above_horizon = is_above_horizon(p, direction);
    vec4 ray_scatter_index_p = ray_scatter_forward(p, direction, light_direction, above_horizon);
    vec3 ray_scatter_p = interpolate_4d(ray_scatter, height_size, elevation_size, light_elevation_size, heading_size,
                                        ray_scatter_index_p).rgb;
    vec4 ray_scatter_index_q = ray_scatter_forward(q, direction, light_direction, above_horizon);
    vec3 ray_scatter_q = interpolate_4d(ray_scatter, height_size, elevation_size, light_elevation_size, heading_size,
                                        ray_scatter_index_q).rgb;
    vec3 transmittance_p_q = transmittance_track(p, q);
    return ray_scatter_p - transmittance_p_q * ray_scatter_q;
  } else
    return vec3(0, 0, 0);
}
