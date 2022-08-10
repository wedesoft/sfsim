#version 410 core

uniform float anisotropic;
uniform int cloud_base_samples;

vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
float cloud_density(vec3 point);
float phase(float g, float mu);

vec3 cloud_track_base(vec3 origin, vec3 light_direction, float a, float b, vec3 incoming)
{
  float dist = b - a;
  if (dist > 0) {
    vec3 p = origin + a * light_direction;
    vec3 q = origin + b * light_direction;
    float stepsize = dist / cloud_base_samples;
    vec3 delta = light_direction * stepsize;
    float scatter_amount = anisotropic * phase(0.76, -1) + 1 - anisotropic;
    vec3 transmittance_atmosphere = transmittance_track(p, q);
    vec3 ray_scatter_atmosphere = ray_scatter_track(light_direction, p, q);
    incoming = incoming * transmittance_atmosphere + ray_scatter_atmosphere;
    vec3 b = p;
    vec3 c = p + delta * 0.5;
    for (int i=0; i<cloud_base_samples; i++) {
      vec3 a = b;
      b = a + delta;
      c = c + delta;
      float density = cloud_density(c);
      float transmittance_cloud = exp((scatter_amount - 1) * density * stepsize);
      incoming = incoming * transmittance_cloud;
    };
    return incoming;
  } else
    return incoming;
}
