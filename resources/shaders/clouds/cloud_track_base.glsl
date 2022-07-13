#version 410 core

uniform float anisotropic;
uniform int cloud_base_samples;

vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
float cloud_density(vec3 point);
float phase(float g, float mu);

vec3 cloud_track_base(vec3 p, vec3 q, vec3 incoming)
{
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 light_direction = (q - p) / dist;
    vec3 delta = (q - p) / cloud_base_samples;
    float stepsize = dist / cloud_base_samples;
    for (int i=cloud_base_samples-1; i>=0; i--) {
      vec3 a = p + delta * i;
      vec3 b = a + delta;
      vec3 c = 0.5 * (a + b);
      vec3 transmittance_atmosphere = transmittance_track(a, b);
      vec3 ray_scatter_atmosphere = ray_scatter_track(light_direction, a, b);
      float density = cloud_density(c);
      float scatter_amount = anisotropic * phase(0.76, -1) + 1 - anisotropic;
      float transmittance_cloud = exp((scatter_amount - 1) * density * stepsize);
      incoming = incoming * transmittance_atmosphere * transmittance_cloud + ray_scatter_atmosphere;
    };
    return incoming;
  } else
    return incoming;
}
