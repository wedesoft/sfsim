#version 410 core

uniform float anisotropic;
uniform int cloud_samples;

bool is_above_horizon(vec3 point, vec3 direction);
vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
float cloud_density(vec3 point);
vec3 cloud_shadow(vec3 point, vec3 light_direction);
float phase(float g, float mu);

vec3 cloud_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming)
{
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    vec3 delta = (q - p) / cloud_samples;
    float stepsize = dist / cloud_samples;
    for (int i=cloud_samples-1; i>=0; i--) {
      bool above = is_above_horizon(p, direction);
      vec3 a = p + delta * i;
      vec3 b = a + delta;
      vec3 c = 0.5 * (a + b);
      vec3 transmittance_atmosphere = transmittance_track(a, b);
      vec3 ray_scatter_atmosphere = ray_scatter_track(light_direction, a, b);
      float density = cloud_density(c);
      float transmittance_cloud = exp(-density * stepsize);
      vec3 intensity = cloud_shadow(c, light_direction);
      float scatter_amount = anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic;
      vec3 cloud_scatter = (1 - transmittance_cloud) * scatter_amount * intensity;
      incoming = incoming * transmittance_atmosphere * transmittance_cloud + ray_scatter_atmosphere + cloud_scatter;
    };
  };
  return incoming;
}
