#version 450 core

<% (if (not full) %>
uniform float z_far;
<% ) %>
<% (if full %>
uniform vec3 light_direction;
uniform float specular;
<% ) %>

in VS_OUT
{
  vec3 direction;
} fs_in;

layout (location = 0) out vec4 camera_point;
<% (if (not full) %>
layout (location = 1) out float dist;
<% ) %>
<% (if full %>
layout (location = 1) out vec4 camera_normal;
layout (location = 2) out vec4 diffuse_material;
layout (location = 3) out float metallic_material;
layout (location = 4) out float specular_material;
layout (location = 5) out vec4 emissive_material;
<% ) %>

<% (if full %>
vec3 sun_color(vec3 direction)
{
  float glare = pow(max(0, dot(direction, light_direction)), specular);
  return vec3(glare, glare, glare);
}
<% ) %>

void main()
{
  vec3 direction = normalize(fs_in.direction);
  camera_point = vec4(direction, 0.0);
<% (if (not full) %>
  dist = z_far;
<% ) %>
<% (if full %>
  camera_normal = vec4(0, 0, 0, 0);
  diffuse_material = vec4(0, 0, 0, 0);
  metallic_material = 0.0;
  vec3 incoming = sun_color(direction);
  emissive_material = vec4(incoming, 0);
<% ) %>
}
