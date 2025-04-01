#version 410 core

uniform vec3 light_direction;
uniform float amplification;

float planet_and_cloud_shadows(vec4 point);
vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);

vec4 cloud_transfer(vec3 start, vec3 point, float scatter_amount, float stepsize, vec4 cloud_scatter, float density)
{
  vec3 light_received = planet_and_cloud_shadows(vec4(point, 1)) * transmittance_outer(point, light_direction) * transmittance_track(start, point) * scatter_amount;
  vec3 in_scatter = ray_scatter_track(light_direction, start, point) * amplification;
  float cloud_transmittance = exp(-density * stepsize);
  float x = -log(cloud_scatter.a * sqrt(cloud_transmittance));
  float powder = 1.0 - exp(-x * 6);
  cloud_scatter.rgb += powder * cloud_scatter.a * (1 - cloud_transmittance) * (light_received + in_scatter);
  cloud_scatter.a *= cloud_transmittance;
  return cloud_scatter;
}
