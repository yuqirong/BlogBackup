title: Vue.js模板方法
date: 2017-12-02 20:36:02
categories: Vue.js
tags: Vue.js
---
v-html
------
将 html 的代码输出

	<div id="app">
	    <div v-html="message"></div>
	</div>
	    
	<script>
	new Vue({
	  el: '#app',
	  data: {
	    message: '<h1>Hello World</h1><img src="https://www.baidu.com/img/bd_logo1.png" />'
	  }
	})
	</script>

v-bind
------
使用 v-bind 指令赋值给 HTML 属性

	<div id="app">
		<img v-bind:src="imgurl" />
    	<h1 v-bind:class="{'img_class': useClass}">Hello</h1>
	</div>
	
	<script>
	new Vue({
	  el: '#app',
	  data: {
	    imgurl: 'https://www.baidu.com/img/bd_logo1.png',
		useClass: true
	  }
	})
	</script>

	<style>
	.img_class {
	  background: #444;
	}
	</style>

v-if
----
用于判断条件

	<img id="app" src="https://www.baidu.com/img/bd_logo1.png" v-if="visible"/>

	<script>
	new Vue({
		el: '#app',
		data: {
			visible: true
		}
	})
	</script>

v-else-if/v-else
----------------
用于判断条件

	<div id="app">
	    <div v-if="type === 'A'">
	      A
	    </div>
	    <div v-else-if="type === 'B'">
	      B
	    </div>
	    <div v-else-if="type === 'C'">
	      C
	    </div>
	    <div v-else>
	      Not A/B/C
	    </div>
	</div>
	    
	<script>
	new Vue({
	  el: '#app',
	  type: 'C'
	})
	</script>

v-show
------
可以使用 v-show 指令来根据条件展示元素，

用法上和 v-if 差不多，但是 v-if 是动态的向 DOM 树内添加或者删除 DOM 元素。 而 v-show 是通过设置 DOM 元素的 display 样式属性控制显隐。

关于 v-show 和 v-if 的区别，详见 [v-if 和 v-show的区别](http://blog.csdn.net/ning0_o/article/details/56006528) 。

	<div id="app">
	    <h1 v-show="ok">Hello!</h1>
	</div>
		
	<script>
	new Vue({
	  el: '#app',
	  data: {
	    ok: true
	  }
	})
	</script>

v-model
-------
v-model 指令来实现双向数据绑定。

	<div id="app">
	    <p>{{ message }}</p>
	    <input v-model="message">
	</div>
		
	<script>
	new Vue({
	  el: '#app',
	  data: {
	    message: 'Hello World'
	  }
	})
	</script>

v-on
----
使用 v-on 监听事件

	<div id="app">
		<button v-on:click="onclick">{{message}}</button>
	</div>
	
	<script>
	new Vue({
	  el: '#app',
	  data: {
	    message: 'Click Me'
	  },
	  methods: {
		onclick: function(){
			alert("Hello")
		}
	  }
	})
	</script>

v-for
-----
循环遍历

	<div id="app">
	  <ol>
	    <li v-for="site in sites">
	      {{ site.name }}
	    </li>
	  </ol>
	</div>
	 
	<script>
	new Vue({
	  el: '#app',
	  data: {
	    sites: [
	      { name: 'Apple' },
	      { name: 'Google' },
	      { name: 'Taobao' }
	    ]
	  }
	})
	</script>

或者

	<div id="app">
	  <ul>
	    <li v-for="(key,value, index) in object">
	     {{ index }}. {{ key }} : {{ value }}
	    </li>
	  </ul>
	</div>
	
	<script>
	new Vue({
	  el: '#app',
	  data: {
	    object: {
	      name: 'Hello',
	      url: 'World',
	      slogan: 'Vue.js'
	    }
	  }
	})
	</script>

缩写
----
* v-bind 缩写

		<!-- 完整语法 -->
		<a v-bind:href="url"></a>
		<!-- 缩写 -->
		<a :href="url"></a>

* v-on 缩写

		<!-- 完整语法 -->
		<a v-on:click="doSomething"></a>
		<!-- 缩写 -->
		<a @click="doSomething"></a>