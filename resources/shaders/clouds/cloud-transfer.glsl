#version 410 core

uniform vec3 light_direction;
uniform float amplification;

float planet_and_cloud_shadows(vec4 point);
vec3 transmittance_outer(vec3 point, vec3 direction);
float powder(float d);
vec3 attenuate(vec3 light_direction, vec3 start, vec3 point, vec3 incoming);

vec4 cloud_transfer(vec3 start, vec3 point, float scatter_amount, float stepsize, vec4 cloud_scatter, float density)
{
  vec3 illumination = planet_and_cloud_shadows(vec4(point, 1)) * transmittance_outer(point, light_direction);
  vec3 cloud_color = illumination * scatter_amount * powder(density * stepsize);
  float cloud_transmittance = exp(-density * stepsize);
  cloud_scatter.rgb += cloud_scatter.a * (1 - cloud_transmittance) * attenuate(light_direction, start, point, cloud_color);
  cloud_scatter.a *= cloud_transmittance;
  return cloud_scatter;
}
