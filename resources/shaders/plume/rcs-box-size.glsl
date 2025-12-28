#version 450 core

#define START 0.0
#define END <%= rcs-end %>

uniform float nozzle;
uniform float pressure;
uniform float throttle;
uniform float max_slope;

float rcs_limit(float pressure);

vec3 rcs_box_size()
{
  float box_size = min(rcs_limit(pressure), throttle * (START - END) * max_slope + nozzle);
  return vec3(box_size, mix(START, END, throttle), START);
}
