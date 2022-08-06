#version 410 core

uniform float anisotropic;
uniform int cloud_samples;
uniform float transparency_cutoff;

vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
float cloud_density(vec3 point);
vec3 cloud_shadow(vec3 point, vec3 light_direction);
float phase(float g, float mu);

vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  float dist = b - a;
  if (dist > 0) {
    vec3 p = origin + a * direction;
    vec3 q = origin + b * direction;
    float stepsize = dist / cloud_samples;
    vec3 delta = direction * stepsize;
    vec3 ray_scatter_atmosphere = ray_scatter_track(light_direction, p, q);
    vec3 transmittance_atmosphere = transmittance_track(p, q);
    incoming = incoming * transmittance_atmosphere + ray_scatter_atmosphere;
    float transparency = 1.0;
    vec3 cloud_scatter = vec3(0, 0, 0);
    vec3 b = p;
    vec3 c = p + delta * 0.5;
    for (int i=0; i<cloud_samples; i++) {
      vec3 a = b;
      b = a + delta;
      c = c + delta;
      float density = cloud_density(c);
      if (density > 0) {
        float transmittance_cloud = exp(-density * stepsize);
        vec3 intensity = cloud_shadow(c, light_direction);
        float scatter_amount = anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic;
        cloud_scatter = cloud_scatter + transparency * (1 - transmittance_cloud) * scatter_amount * intensity;
        transparency = transparency * transmittance_cloud;
      };
      if (transparency <= transparency_cutoff)
        break;
    };
    incoming = incoming * transparency + cloud_scatter;
  };
  return incoming;
}
