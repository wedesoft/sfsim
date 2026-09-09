#version 450 core

<% (when normals %>
in VS_OUT
{
  vec4 normal;
} fs_in;
<% ) %>

<% (when normals %>
layout (location = 0) out vec4 normals;
<% ) %>

void main()
{
<% (when normals %>
  normals = fs_in.normal;
<% ) %>
}
