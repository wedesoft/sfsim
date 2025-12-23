#version 450 core

#define START <%= plume-start %>
#define END <%= plume-end %>
#define WIDTH2 <%= plume-width-2 %>

uniform float nozzle;
uniform float pressure;
uniform float throttle;
uniform float max_slope;

float limit(float pressure);
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);

vec2 plume_box(vec3 origin, vec3 direction)
{
  float box_size = min(max(limit(pressure), nozzle) - nozzle, throttle * (START - END) * max_slope) + WIDTH2;
  return ray_box(vec3(mix(START, END, throttle), -box_size, -box_size), vec3(START, box_size, box_size), origin, direction);
}

