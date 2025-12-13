#version 450 core

uniform vec3 origin;
uniform mat4 camera_to_world;
uniform float object_distance;

vec4 geometry_point();
vec4 cloud_point(vec3 origin, vec3 direction, vec2 segment);
vec4 cloud_outer(vec3 origin, vec3 direction, float skip);

out vec4 fragColor;

void main()
{
  vec3 direction = (camera_to_world * geometry_point()).xyz;
<% (if front %>
  fragColor = cloud_point(origin, direction, vec2(0, object_distance));
<% %>
  fragColor = cloud_outer(origin, direction, object_distance);
<% ) %>
}
