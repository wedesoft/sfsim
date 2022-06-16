#version 410 core

vec3 ray_scatter_forward(vec3 point, vec3 direction);
vec3 transmittance_forward(vec3 point, vec3 direction);
float cloud_density(vec3 point);
vec3 clouded_light(vec3 point, vec3 light);

vec3 cloud_track(vec3 p, vec3 q, int n, vec3 background, vec3 light)
{
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    vec3 delta = (q - p) / n;
    float stepsize = dist / n;
    for (int i=0; i<n; i++) {
      vec3 a = p + delta * i;
      vec3 b = a + delta;
      vec3 c = 0.5 * (a + b);
      vec3 transmittance_atmosphere_a = transmittance_forward(a, direction);
      vec3 transmittance_atmosphere_b = transmittance_forward(b, direction);
      vec3 transmittance_atmosphere = transmittance_atmosphere_a / transmittance_atmosphere_b;
      vec3 ray_scatter_a = ray_scatter_forward(a, direction);
      vec3 ray_scatter_b = ray_scatter_forward(b, direction);
      vec3 scattering_atmosphere = ray_scatter_a - ray_scatter_b * transmittance_atmosphere;
      float density = cloud_density(c);
      float transmittance_cloud = exp(-density * stepsize);
      float cloud_scatter = 1 - transmittance_cloud;
      vec3 intensity = clouded_light(c, light);
      background = background * transmittance_atmosphere * transmittance_cloud + scattering_atmosphere + intensity * cloud_scatter;
    };
  };
  return background;
}
