#version 450 core

<% (if full %>
uniform vec3 diffuse_color;
<% ) %>

in VS_OUT
{
  vec4 camera_point;
<% (if full %>
  vec4 normal;
<% ) %>
} fs_in;

layout (location = 0) out vec4 camera_point;
<% (if (not full) %>
layout (location = 1) out float dist;
<% ) %>
<% (if full %>
layout (location = 1) out vec4 camera_normal;
layout (location = 2) out vec4 diffuse_material;
<% ) %>

void main()
{
  camera_point = fs_in.camera_point;
<% (if full %>
  camera_normal = fs_in.normal;
<% ) %>
<% (if (not full) %>
  dist = length(fs_in.camera_point.xyz);
<% ) %>
}
