#version 410 core

uniform float anisotropic;
uniform int cloud_samples;
uniform float cloud_scatter_amount;
uniform float cloud_max_dist;
uniform float cloud_max_step;
uniform float transparency_cutoff;

vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
float cloud_density(vec3 point, float lod);
vec3 cloud_shadow(vec3 point, vec3 light_direction, float lod);
float phase(float g, float mu);
int number_of_steps(float a, float b, int max_samples, float max_step);
float scaling_offset(float a, float b, int samples, float min_step);
float step_size(float a, float b, float scaling_offset, int num_steps);
float next_point(float p, float scaling_offset, float step_size);
float initial_lod(float a, float scaling_offset, float step_size);
float lod_increment(float step_size);

vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  float dist = min(cloud_max_dist, b - a);
  if (dist > 0) {
    b = a + dist;
    vec3 p = origin + a * direction;
    vec3 q = origin + b * direction;
    vec3 ray_scatter_atmosphere = ray_scatter_track(light_direction, p, q);
    vec3 transmittance_atmosphere = transmittance_track(p, q);
    incoming = incoming * transmittance_atmosphere + ray_scatter_atmosphere;
    float transparency = 1.0;
    int samples = number_of_steps(a, b, cloud_samples, cloud_max_step);
    float scale_offset = scaling_offset(a, b, samples, cloud_max_step);
    float stepping = step_size(a, b, scale_offset, samples);
    float lod = initial_lod(a, scale_offset, stepping);
    float lod_incr = lod_increment(stepping);
    vec3 cloud_scatter = vec3(0, 0, 0);
    float b_step = a;
    for (int i=0; i<samples; i++) {
      float a_step = b_step;
      b_step = next_point(b_step, scale_offset, stepping);
      vec3 c = origin + 0.5 * (a_step + b_step) * direction;
      float density = cloud_density(c, lod);
      if (density > 0) {
        float stepsize = b_step - a_step;
        float transmittance_cloud = exp(-density * stepsize);
        vec3 intensity = cloud_shadow(c, light_direction, lod);
        float scatter_amount = (anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic) * cloud_scatter_amount;
        cloud_scatter = cloud_scatter + transparency * (1 - transmittance_cloud) * scatter_amount * intensity;
        transparency = transparency * transmittance_cloud;
      };
      if (transparency <= transparency_cutoff)
        break;
      lod = lod + lod_incr;
    };
    incoming = incoming * max(0, transparency - transparency_cutoff) + cloud_scatter;
  };
  return incoming;
}
