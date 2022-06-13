#version 410 core

vec3 ray_scatter_forward(vec3 point, vec3 direction);
float transmittance_forward(vec3 point, vec3 direction);

vec3 cloud_track(vec3 p, vec3 q, int n, vec3 light)
{
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    float transmittance_p = transmittance_forward(p, direction);
    float transmittance_q = transmittance_forward(q, direction);
    float transmittance_p_q = transmittance_p / transmittance_q;
    vec3 ray_scatter_p = ray_scatter_forward(p, direction);
    vec3 ray_scatter_q = ray_scatter_forward(q, direction);
    vec3 in_scattering = ray_scatter_p - ray_scatter_q * transmittance_p_q;
    return light * transmittance_p_q + in_scattering;
  } else
    return light;
}
