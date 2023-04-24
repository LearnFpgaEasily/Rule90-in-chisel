package rule90

import chisel3._
import chisel3.util._
import scala.util._

class PulseGenerator(pulsePeriod: Int) extends Module{
    val io = IO(new Bundle{
        val pulse = Output(UInt(1.W))
    })
    val pulse = RegInit(0.U(log2Ceil(pulsePeriod).W))
    pulse := Mux(pulse===pulsePeriod.U, 0.U, pulse+1.U)
    io.pulse := pulse===pulsePeriod.U
}

class cellAutomataBundle(numCells: Int) extends Bundle{
    val i_cells = Input(Vec(numCells, UInt  (1.W)))
    val o_cells = Output(Vec(numCells, UInt(1.W)))
}

class Rule90(numCells: Int) extends Module {
    val io = IO(new cellAutomataBundle(numCells))
    
    for(idx <- 0 until numCells){
        val previous_idx = Math.floorMod(idx - 1, numCells)
        val next_idx     = Math.floorMod(idx + 1, numCells)
        io.o_cells(idx) := RegNext(io.i_cells(previous_idx) ^ io.i_cells(next_idx), 0.U)   
    }
}


class AlchitryCUTop extends Module {
    val io = IO(new Bundle{
        val ledPins = Output(Vec(24, UInt(1.W)))
    })
    val fpgaFreq = 100000000
    val numLeds  = 24

    // the alchitry CU board has an active low reset
    val reset_n = !reset.asBool
    withReset(reset_n){
        val rule90 = Module(new Rule90(numLeds))
        val next_generation = Module(new PulseGenerator(fpgaFreq))
        for(idx <- 0 until numLeds){
            rule90.io.i_cells(idx) := RegEnable(rule90.io.o_cells(idx), (Random.nextInt(100)%2).U, next_generation.io.pulse.asBool) //(Random.nextInt(100)%2).U
        }
        io.ledPins <> rule90.io.o_cells
    }
}

object Main extends App{
    (new chisel3.stage.ChiselStage).emitVerilog(new AlchitryCUTop, Array("--target-dir", "build/artifacts/netlist/"))
}