---
layout: page
title: News
permalink: /news/
---

Check out our latest updates and news stories below:

<ul style="list-style: none; padding-left: 0;">
  {% for post in site.posts %}
    <li style="display: flex; margin-bottom: 8px;">
      <span class="post-date" style="width: 150px; flex-shrink: 0; color: #666;">
        {{ post.date | date: "%B %d, %Y" }}
      </span>
      <span style="flex-grow: 1;">
        <a href="{{ post.url | relative_url }}">{{ post.title }}</a>
      </span>
    </li>
  {% endfor %}
</ul>
