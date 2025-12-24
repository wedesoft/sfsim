#version 450 core

#define START <%= plume-start %>
#define END <%= plume-end %>
#define WIDTH2 <%= plume-width-2 %>

uniform float nozzle;
uniform float pressure;
uniform float throttle;
uniform float max_slope;

float limit(float pressure);

vec3 plume_box_size()
{
  float box_size = min(max(limit(pressure), nozzle) - nozzle, throttle * (START - END) * max_slope) + WIDTH2;
  return vec3(box_size, mix(START, END, throttle), START);
}
