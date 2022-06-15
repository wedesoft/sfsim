#version 410 core

vec3 ray_scatter_forward(vec3 point, vec3 direction);
vec3 transmittance_forward(vec3 point, vec3 direction);
float cloud_density(vec3 point);

vec3 cloud_track(vec3 p, vec3 q, int n, vec3 background)
{
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    vec3 delta = (q - p) / n;
    float stepsize = dist / n;
    for (int i=0; i<n; i++) {
      vec3 a = p + delta * i;
      vec3 b = a + delta;
      vec3 transmittance_a = transmittance_forward(a, direction);
      vec3 transmittance_b = transmittance_forward(b, direction);
      vec3 transmittance_a_b = transmittance_a / transmittance_b;
      vec3 ray_scatter_a = ray_scatter_forward(a, direction);
      vec3 ray_scatter_b = ray_scatter_forward(b, direction);
      vec3 in_scattering_atmosphere = ray_scatter_a - ray_scatter_b * transmittance_a_b;
      background = background * transmittance_a_b + in_scattering_atmosphere;
    };
  };
  return background;
}
