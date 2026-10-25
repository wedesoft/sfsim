#version 450 core

uniform float shockwave_radius;
uniform float shockwave_step;

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
vec4 shockwave_transfer(vec3 p, float shockwave_step, vec4 shockwave_scatter);
float sampling_offset();
<% (when (not outer) %>
vec2 limit_interval(vec2 interval, float limit);
<% ) %>

<% (when outer %>
vec4 sample_shockwave_outer(vec3 object_origin, vec3 object_direction)
<% ) %>
<% (when (not outer) %>
vec4 sample_shockwave_point(vec3 object_origin, vec3 object_direction, float dist)
<% ) %>
{
  vec2 segment = ray_box(vec3(-shockwave_radius, -shockwave_radius, 0),
                         vec3(shockwave_radius, shockwave_radius, 2 * shockwave_radius),
                         object_origin, object_direction);
<% (when (not outer) %>
  segment = limit_interval(segment, dist);
<% ) %>
  vec4 result = vec4(0, 0, 0, 0);
  float x = segment.x + shockwave_step * sampling_offset();
  while (x < segment.x + segment.y) {
    vec3 sample_point = object_origin + x * object_direction;
    result = shockwave_transfer(sample_point, shockwave_step, result);
    x += shockwave_step;
  };
  return result;
}
