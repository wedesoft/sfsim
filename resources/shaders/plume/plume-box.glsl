#version 410 core

#define START <%= plume-start %>
#define END <%= plume-end %>
#define WIDTH2 <%= plume-width-2 %>

uniform float nozzle;
uniform float pressure;

float limit(float pressure);
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);

vec2 plume_box(vec3 origin, vec3 direction)
{
  float box_size = max(limit(pressure), nozzle) + WIDTH2 - nozzle;
  return ray_box(vec3(END, -box_size, -box_size), vec3(START, box_size, box_size), origin, direction);
}

