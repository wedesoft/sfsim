#version 450 core

uniform float rcs_step;

vec2 rcs_box(vec3 origin, vec3 direction);
vec4 rcs_transfer(vec3 point, float rcs_step, vec4 rcs_scatter);
float sampling_offset();
vec2 limit_interval(vec2 interval, float limit);

<% (if outer %>
vec4 sample_rcs_outer(vec3 object_origin, vec3 object_direction)
<% %>
vec4 sample_rcs_point(vec3 object_origin, vec3 object_direction, float dist)
<% ) %>
{
  vec2 segment = rcs_box(object_origin, object_direction);
<% (if (not outer) %>
  segment = limit_interval(segment, dist);
<% ) %>
  vec4 result = vec4(0, 0, 0, 1);
  float x = segment.x + sampling_offset() * rcs_step;
  while (x < segment.x + segment.y) {
    vec3 point = object_origin + x * object_direction;
    result = rcs_transfer(point, rcs_step, result);
    x += rcs_step;
  };
  return vec4(result.rgb, 1 - result.a);
}

