#version 410 core

uniform float anisotropic;
uniform int cloud_base_samples;

vec3 transmittance_forward(vec3 point, vec3 direction);
vec3 ray_scatter_forward(vec3 point, vec3 direction, vec3 light);
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
      vec3 transmittance_atmosphere_a = transmittance_forward(a, light_direction);
      vec3 transmittance_atmosphere_b = transmittance_forward(b, light_direction);
      vec3 transmittance_atmosphere = transmittance_atmosphere_a / transmittance_atmosphere_b;
      vec3 ray_scatter_a = ray_scatter_forward(a, light_direction, light_direction);
      vec3 ray_scatter_b = ray_scatter_forward(b, light_direction, light_direction);
      vec3 ray_scatter_atmosphere = ray_scatter_a - ray_scatter_b * transmittance_atmosphere;
      float density = cloud_density(c);
      float scatter_amount = anisotropic * phase(0.76, -1) + 1 - anisotropic;
      float transmittance_cloud = exp((scatter_amount - 1) * density * stepsize);
      incoming = incoming * transmittance_atmosphere * transmittance_cloud + ray_scatter_atmosphere;
    };
    return incoming;
  } else
    return incoming;
}
