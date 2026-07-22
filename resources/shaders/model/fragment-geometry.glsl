#version 450 core

<% (if (and full (not textured)) %>
uniform vec3 diffuse_color;
<% ) %>
<% (if (and full textured) %>
uniform sampler2D colors;
<% ) %>
<% (if (and full bump) %>
uniform sampler2D normals;
<% ) %>

in VS_OUT
{
  vec4 camera_point;
<% (if (and full (not bump)) %>
  vec4 normal;
<% ) %>
<% (if (and full bump) %>
  mat3 surface;
<% ) %>
<% (if (and full (or textured bump)) %>
  vec2 texcoord;
<% ) %>
} fs_in;

layout (location = 0) out vec4 camera_point;
<% (if (not full) %>
layout (location = 1) out float dist;
<% ) %>
<% (if full %>
layout (location = 1) out vec4 camera_normal;
layout (location = 2) out vec4 diffuse_material;
layout (location = 3) out float specular_material;
layout (location = 4) out vec4 emissive_material;
<% ) %>

void main()
{
  camera_point = fs_in.camera_point;
<% (if (and full (not bump)) %>
  camera_normal = fs_in.normal;
<% ) %>
<% (if (and full bump) %>
  vec3 normal = 2.0 * texture(normals, fs_in.texcoord).xyz - 1.0;
  camera_normal = vec4(fs_in.surface * normal, 0.0);
<% ) %>
<% (if (and full (not textured)) %>
  diffuse_material = vec4(diffuse_color, 1.0);
<% ) %>
<% (if (and full textured) %>
  diffuse_material = vec4(texture(colors, fs_in.texcoord).rgb, 1.0);
<% ) %>
<% (if full %>
  specular_material = 0.0;
  emissive_material = vec4(0, 0, 0, 0);
<% ) %>
<% (if (not full) %>
  dist = length(fs_in.camera_point.xyz);
<% ) %>
}
