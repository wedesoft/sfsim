#version 450 core

#define START 0.0
#define BASE_DENSITY <%= base-density %>
#define RCS_END <%= rcs-end %>
#define SPEED 50.0
#define NOZZLE 0.1

uniform float pressure;
uniform float rcs_throttle;
uniform float time;

float rcs_bulge(float pressure, float x);
float noise3d(vec3 coordinates);

vec4 rcs_transfer(vec3 point, float rcs_step, vec4 rcs_scatter)
{
  float radius = rcs_bulge(pressure, -point.x);
  if (length(point.yz) <= radius) {
    float end = mix(START, RCS_END, rcs_throttle);
    float fade = clamp((point.x - end) / (START - end), 0.0, 1.0);
    float density = BASE_DENSITY / (radius * radius) * fade;
    vec3 scale = 100.0 * vec3(0.1, NOZZLE / radius, NOZZLE / radius);
    float attenuation = 0.7 + 0.3 * noise3d(point * scale + time * vec3(-SPEED, 0.0, 0.0));
    float emitting = density * rcs_step * attenuation;
    float rcs_transmittance = exp(-density * rcs_step);
    return vec4(rcs_scatter.rgb + emitting, rcs_scatter.a * rcs_transmittance);
  } else
    return rcs_scatter;
}
