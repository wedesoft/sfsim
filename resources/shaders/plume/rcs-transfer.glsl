#version 450 core

#define BASE_DENSITY <%= base-density %>
#define RCS_END <%= rcs-end %>
#define SPEED 50.0
#define NOZZLE 0.1

uniform float pressure;
uniform float time;

float bulge(float pressure, float x);
float noise3d(vec3 coordinates);

vec4 rcs_transfer(vec3 point, float rcs_step, vec4 rcs_scatter)
{
  float radius = bulge(pressure, -point.x);
  if (length(point.yz) <= radius) {
    float fade = mix(1.0, 0.0, point.x / RCS_END);
    float density = BASE_DENSITY / (radius * radius) * fade;
    vec3 scale = 100.0 * vec3(0.1, NOZZLE / radius, NOZZLE / radius);
    float attenuation = 0.7 + 0.3 * noise3d(point * scale + time * vec3(-SPEED, 0.0, 0.0));
    float emitting = density * rcs_step * attenuation;
    float rcs_transmittance = exp(-density * rcs_step);
    return vec4(rcs_scatter.rgb + emitting, rcs_scatter.a * rcs_transmittance);
  } else
    return rcs_scatter;
}
