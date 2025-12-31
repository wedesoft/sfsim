#version 450 core

uniform vec3 origin;
uniform vec3 object_origin;
uniform mat4 camera_to_world;
uniform mat4 camera_to_object;
uniform mat4 object_to_<%= type %>;

vec4 geometry_point();
float geometry_distance();
vec4 <%= type %>_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction);
vec4 <%= type %>_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist);

out vec4 fragColor;

void main()
{
  vec4 camera_direction = geometry_point();
  vec3 direction = (camera_to_world * camera_direction).xyz;
  vec3 object_origin_<%= type %> = (object_to_<%= type %> * vec4(object_origin, 1)).xyz;
  vec3 object_direction_<%= type %> = (object_to_<%= type %> * camera_to_object * camera_direction).xyz;
<% (when outer %>
  fragColor = <%= type %>_outer(origin, direction, object_origin_<%= type %>, object_direction_<%= type %>);
<% ) %>
<% (when (not outer) %>
  float dist = geometry_distance();
  fragColor = <%= type %>_point(origin, direction, object_origin_<%= type %>, object_direction_<%= type %>, dist);
<% ) %>
}
