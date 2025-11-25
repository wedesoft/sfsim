#version 450 core

uniform vec3 light_direction;
uniform float amplification;

float planet_and_cloud_shadows(vec4 point);
vec3 transmittance_outer(vec3 point, vec3 direction);
float powder(float d);
vec4 attenuate(vec3 light_direction, vec3 start, vec3 point, vec4 incoming);

vec4 cloud_transfer(vec3 start, vec3 point, float scatter_amount, float stepsize, vec4 cloud_scatter, float density)
{
  vec3 illumination = planet_and_cloud_shadows(vec4(point, 1)) * transmittance_outer(point, light_direction);
  float cloud_transmittance = exp(-density * stepsize);
  vec4 cloud_color = vec4(illumination * scatter_amount * powder(density * stepsize) * (1.0 - cloud_transmittance), 1.0 - cloud_transmittance);
  cloud_scatter.rgb += cloud_scatter.a * attenuate(light_direction, start, point, cloud_color).rgb;
  cloud_scatter.a *= 1.0 - cloud_color.a;
  return cloud_scatter;
}
