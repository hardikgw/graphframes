import { Component, OnInit } from '@angular/core';
import APP_CONFIG from '../../app.config';
import { Node } from './node';
import { Link } from './link'
import { GraphframesService } from "../spark/graphframes.service";

@Component({
  selector: 'simple-graph',
  templateUrl: './simple-graph.component.html',
  styleUrls: ['./simple-graph.component.css'],
  providers:[GraphframesService]
})
export class SimpleGraphComponent implements OnInit {

  nodes: Node[] = [];
  links: Link[] = [];

  populateNodes(vertices:any) {
    vertices.forEach((vertice)=>{
      this.nodes.push(new Node(vertice.id));
    })
  }

  populateLinks(edges:any){
    for(let i = 0; i < edges.length; i++) {
      this.nodes[edges[i].src].linkCount++;
      this.nodes[edges[i].dst].linkCount++;
      this.links.push(new Link(edges[i].src, edges[i].dst));
    }
  }

  constructor(private graphService : GraphframesService) {

    const N = APP_CONFIG.N,
      getIndex = number => number - 1;

    graphService.getData().subscribe(data=>{
      let vertices = data.vertices;
      let edges = data.edges;

      let nodes: Node[] = [];
      let links: Link[] = [];

      for(let i = 0; i < vertices.length; i++) {
        nodes.push(new Node(i));
      }

      for(let i = 0; i < edges.length; i++) {
        nodes[edges[i].source].linkCount++;
        nodes[edges[i].target].linkCount++;
        links.push(new Link(edges[i].source, edges[i].target));
      }
      this.nodes = nodes;
      this.links = links;
    });

    // /** constructing the nodes array */
    // for (let i = 1; i <= N; i++) {
    //   this.nodes.push(new Node(i));
    // }
    //
    // for (let i = 1; i <= N; i++) {
    //   for (let m = 2; i * m <= N; m++) {
    //     /** increasing connections toll on connecting nodes */
    //     this.nodes[getIndex(i)].linkCount++;
    //     this.nodes[getIndex(i * m)].linkCount++;
    //
    //     /** connecting the nodes before starting the simulation */
    //     this.links.push(new Link(i, i * m));
    //   }
    // }
  }

  ngOnInit() {}

}
