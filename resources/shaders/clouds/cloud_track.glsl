#version 410 core

vec3 ray_scatter_forward(vec3 point, vec3 direction);
float transmittance_forward(vec3 point, vec3 direction);

vec3 cloud_track(vec3 p, vec3 q, int n, vec3 light)
{
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    vec3 delta = (q - p) / n;
    for (int i=0; i<n; i++) {
      vec3 a = p + delta * i;
      vec3 b = a + delta;
      float transmittance_a = transmittance_forward(a, direction);
      float transmittance_b = transmittance_forward(b, direction);
      float transmittance_a_b = transmittance_a / transmittance_b;
      vec3 ray_scatter_a = ray_scatter_forward(a, direction);
      vec3 ray_scatter_b = ray_scatter_forward(b, direction);
      vec3 in_scattering = ray_scatter_a - ray_scatter_b * transmittance_a_b;
      light = light * transmittance_a_b + in_scattering;
    };
  };
  return light;
}
