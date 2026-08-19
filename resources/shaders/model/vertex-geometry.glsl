#version 450 core
uniform mat4 projection;
uniform mat4 object_to_camera;

in vec3 vertex;
<% (if bump %>
in vec3 tangent;
in vec3 bitangent;
<% ) %>
in vec3 normal;
<% (if (or textured bump) %>
in vec2 texcoord;
<% ) %>

out VS_OUT
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
} vs_out;

void main()
{
  vec4 camera_point = object_to_camera * vec4(vertex, 1);
  vs_out.camera_point = camera_point;
<% (if (and full (not bump)) %>
  vs_out.normal = object_to_camera * vec4(normal, 0);
<% ) %>
<% (if (and full bump) %>
  vs_out.surface = mat3(object_to_camera) * mat3(tangent, bitangent, normal);
<% ) %>
<% (if (and full (or textured bump)) %>
  vs_out.texcoord = texcoord;
<% ) %>
  gl_Position = projection * camera_point;
}
