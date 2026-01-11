#version 450 core

#define START 0.3
#define END <%= rcs-end %>

uniform float rcs_nozzle;
uniform float pressure;
uniform float plume_throttle;
uniform float rcs_max_slope;

float rcs_limit(float pressure);

vec3 rcs_box_size()
{
  float box_size = min(rcs_limit(pressure), plume_throttle * (-END) * rcs_max_slope + rcs_nozzle);
  return vec3(box_size, mix(START, END, plume_throttle), START);
}
