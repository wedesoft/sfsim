#version 450 core

#define START 0.0
#define END <%= plume-end %>
#define WIDTH2 <%= plume-width-2 %>

uniform float plume_nozzle;
uniform float pressure;
uniform float plume_throttle;
uniform float max_slope;

float plume_limit(float pressure);

vec3 plume_box_size()
{
  float box_size = min(max(plume_limit(pressure), plume_nozzle) - plume_nozzle, plume_throttle * (START - END) * max_slope) + WIDTH2;
  return vec3(box_size, mix(START, END, plume_throttle), START);
}
