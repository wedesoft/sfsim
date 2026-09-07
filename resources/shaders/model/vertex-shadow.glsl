#version 450 core

uniform mat4 object_to_shadow_ndc;
uniform mat4 object_to_light;
uniform int shadow_size;

in vec3 vertex;
<% (if bump %>
in vec3 tangent;
in vec3 bitangent;
<% ) %>
in vec3 normal;
<% (if (or textured bump) %>
in vec2 texcoord;
<% ) %>

vec4 shrink_shadow_index(vec4 idx, int size_y, int size_x);

<% (when normals %>
out VS_OUT
{
  vec4 normal;
} vs_out;
<% ) %>

void main()
{
  vec4 transformed_point = object_to_shadow_ndc * vec4(vertex, 1);
<% (when normals %>
  vs_out.normal = object_to_light * vec4(normal, 0);
<% ) %>
  gl_Position = shrink_shadow_index(transformed_point, shadow_size, shadow_size);
}
