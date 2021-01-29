// Squirrel application
// --------------------

// Name and version
const header = new Vue({
  el: '#header-left',
  data: {
    name: 'Squirrel',
    version: ''
  },
  created() {
    console.log("fetch version");
    fetch('/version').then(response => response.json())
                     .then(response => this.version = response.version);
    }
})

// Router

// 1. Define route components.
const Projects = {
  template: '<div>{{list}}</div>',
  data() {
    return { projects: '' };
  },
  created() {
    console.log("fetch projects");
    fetch('/projects').then(response => response.json()).then(res => this.projects = res.projects);
  }
}

const Bar = { template: '<div>bar</div>' }

//
//



// 2. Create the router instance and pass the `routes` option
// You can pass in additional options here, but let's
// keep it simple for now.
const router = new VueRouter({
  routes: [
    { path: '/projects', component: Projects },
    { path: '/settings', component: Bar },
    { path: '/people',   component: Bar },
    { path: '/builds',   component: Bar }
  ]
})

// 3. Create and mount the root instance.
// Make sure to inject the router with the router option to make the
// whole app router-aware.
const app = new Vue({
  router
}).$mount('#app')

// Now the app has started!
